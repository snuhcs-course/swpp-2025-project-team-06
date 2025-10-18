import string
from scipy.spatial.distance import cdist
import numpy as np
import json
import sys
import random
from sklearn.cluster import KMeans
from sklearn.ensemble import IsolationForest  # 이상치 탐지를 위해 추가
from sentence_transformers import SentenceTransformer
import os
import shutil
import time
import faiss

IMG_EMB_FILE = "/home/team6/tag-search/PoC/source/embedding_vectors.npy"
FILENAMES_FILE = "/home/team6/tag-search/PoC/source/embedding_ids.json"
ANNOTATION_FILE_1 = "/home/team6/tag-search/PoC/source/tag_info.json"
ANNOTATION_FILE_2 = "/home/team6/tag-search/PoC/source/image_to_words.json"
IMAGE_BASE_PATH = "/home/team6/real_image_testset"
RESULT_BASE_PATH = "/home/team6/tag-search/PoC/results/recommend_with_rep_vec"

p = 1                    # 태그를 온전히 남길 사진의 비율
k = 10                   # k-means 클러스터 개수
outlier_fraction = 0.05  # 이상치(outlier)로 간주할 데이터의 비율 (5%)

# --- 데이터 로딩 및 준비 ---
required_files = [IMG_EMB_FILE, FILENAMES_FILE, ANNOTATION_FILE_1, ANNOTATION_FILE_2]
for fpath in required_files:
    if not os.path.exists(fpath):
        print(f"오류: 필수 파일 '{fpath}'를 찾을 수 없습니다.")
        sys.exit(1)

# 새로운 annotation 파일 로딩 및 병합 로직
img_to_cats = {}
with open(ANNOTATION_FILE_1, "r") as f:
    img_to_cats.update(json.load(f))

with open(ANNOTATION_FILE_2, "r") as f:
    data_to_merge = json.load(f)
    for fname, tags in data_to_merge.items():
        # 기존에 파일명이 있으면 태그 리스트를 합치고, 없으면 새로 추가
        existing_tags = set(img_to_cats.get(fname, []))
        existing_tags.update(tags)
        img_to_cats[fname] = sorted(list(existing_tags))
origin_file_list = list(img_to_cats.keys())

# 임베딩 벡터와 파일명 목록 로딩
images = np.load(IMG_EMB_FILE)
with open(FILENAMES_FILE, "r") as f:
    filenames_list = json.load(f)
filenames = np.array(filenames_list)

# 전체 카테고리 목록 생성 로직
all_categories = sorted(list(set(cat for tags in img_to_cats.values() for cat in tags)))

print("Data loading complete.")

# --- 샘플링 로직 ---
all_fnames_with_tags = list(img_to_cats.keys())
random.shuffle(all_fnames_with_tags)
tag_set = set()
for word_list in img_to_cats.values():
    tag_set.update(word_list)
tag_list = sorted(list(tag_set))


# --- 함수 정의 ---
def compute_vectors_with_outlier_handling(category_name, k, contamination):
    """
    이상치를 먼저 식별하여 대표 벡터로 포함하고,
    나머지 평범한 데이터에 대해서만 K-Means를 실행
    """
    selected_vecs = []
    for fname, vec in zip(filenames, images):
        if category_name in img_to_cats.get(fname, []):
            selected_vecs.append(vec)
    selected_vecs = np.array(selected_vecs)

    # 데이터가 너무 적으면 이상치 탐지가 무의미하므로 모두 반환
    if len(selected_vecs) < 10:
        return selected_vecs

    # 1. IsolationForest로 이상치 탐지
    iso_forest = IsolationForest(contamination=contamination, random_state=42)
    preds = iso_forest.fit_predict(selected_vecs)
    
    # 2. 이상치(outlier)와 정상치(inlier) 분리
    outlier_vecs = selected_vecs[preds == -1]
    inlier_vecs = selected_vecs[preds == 1]
    
    # 3. 정상치에 대해서만 K-Means 실행
    kmeans_centers = np.array([])
    if len(inlier_vecs) >= k:
        kmeans = KMeans(n_clusters=k, random_state=42, n_init='auto')
        kmeans.fit(inlier_vecs)
        kmeans_centers = kmeans.cluster_centers_
    elif len(inlier_vecs) > 0:
        # 정상치가 k개보다 적으면, 모든 정상치를 대표 벡터로 사용
        kmeans_centers = inlier_vecs
        
    # 4. 이상치 벡터와 K-Means 중심점을 합쳐서 최종 대표 벡터 목록 생성
    final_representatives = []
    if len(outlier_vecs) > 0:
        final_representatives.append(outlier_vecs)
    if len(kmeans_centers) > 0:
        final_representatives.append(kmeans_centers)

    if not final_representatives:
        return np.array([])
        
    return np.vstack(final_representatives)


def build_faiss_index(representative_vectors: np.ndarray):
    """대표 벡터들로부터 Faiss 검색 인덱스를 생성"""
    d = representative_vectors.shape[1]
    norms = np.linalg.norm(representative_vectors, axis=1, keepdims=True)
    norms[norms == 0] = 1e-10
    normalized_vectors = (representative_vectors / norms).astype('float32')
    index = faiss.IndexFlatL2(d)
    index.add(normalized_vectors)
    return index

def find_images_by_top_k_tags(search_categories, faiss_index, representative_vector_categories, candidate_vectors, candidate_fnames):
    k_to_find = len(search_categories)
    norms = np.linalg.norm(candidate_vectors, axis=1, keepdims=True)
    norms[norms == 0] = 1e-10
    normalized_candidates = (candidate_vectors / norms).astype('float32')
    _distances, top_k_indices = faiss_index.search(normalized_candidates, k_to_find)
    retrieved_fnames = []
    search_categories_set = set(search_categories)
    for i, fname in enumerate(candidate_fnames):
        top_k_categories = {representative_vector_categories[idx] for idx in top_k_indices[i]}
        if search_categories_set.issubset(top_k_categories):
            retrieved_fnames.append(fname)
    return retrieved_fnames

def copy_images_to_folder(src_paths, dest_folder):
    os.makedirs(dest_folder, exist_ok=True)
    for src_path in src_paths:
        if not os.path.exists(src_path): continue
        dest_path = os.path.join(dest_folder, os.path.basename(src_path))
        shutil.copy2(src_path, dest_path)
        

def get_recommendation_list(search_categories):
    print("\n이상치(Outlier)를 포함하여 대표 벡터 생성 중...")
    all_representative_vectors = []
    representative_vector_categories = []
    for cat in all_categories:
        # 새로운 함수를 호출
        new_vectors = compute_vectors_with_outlier_handling(cat, k=k, contamination=outlier_fraction)
        if len(new_vectors) > 0:
            all_representative_vectors.extend(new_vectors)
            representative_vector_categories.extend([cat] * len(new_vectors))
    all_representative_vectors = np.array(all_representative_vectors)
    
    print("Faiss 인덱스 빌드 중...")
    faiss_index = build_faiss_index(all_representative_vectors)

    final_images_list = find_images_by_top_k_tags(
        search_categories=search_categories,
        faiss_index=faiss_index,
        representative_vector_categories=representative_vector_categories,
        candidate_vectors=images,
        candidate_fnames=filenames
    )
    
    final_retrieved_set = set(final_images_list)
    for fname, actual_tags in img_to_cats.items():
        if all(cat in actual_tags for cat in search_categories):
            final_retrieved_set.add(fname)
    
    final_images_list = sorted(list(final_retrieved_set))
        
    final_images_list_filtered = [img for img in final_images_list if img not in origin_file_list]
    print(f"\n최종 분석 결과, 총 {len(final_images_list_filtered)}개의 이미지를 찾았습니다.")

    output_folder = "_".join(search_categories).replace(" ", "_")
    output_path = os.path.join(RESULT_BASE_PATH, output_folder)
    src_paths = [os.path.join(IMAGE_BASE_PATH, fname) for fname in final_images_list_filtered]
    
    return final_images_list_filtered
    

# --- 메인 실행 블록 ---
if __name__ == "__main__":
    search_categories = sys.argv[1:]
    if not 1 <= len(search_categories) <= 5:
        print("Usage: python PoC_outlier.py <category1> [category2] ... [category5]")
        sys.exit(1)
        
    if search_categories[0] == "category":
        print("사용 가능한 카테고리:", all_categories)
        sys.exit(1)
        
    for cat in search_categories:
        if cat not in all_categories:
            print(f"오류: '{cat}'는 유효한 카테고리가 아닙니다.")
            sys.exit(1)
    print(f"검색 시작: {search_categories}")
    
    list_rep_vec = get_recommendation_list(search_categories=search_categories)
    
    for i in list_rep_vec:
        print(i)