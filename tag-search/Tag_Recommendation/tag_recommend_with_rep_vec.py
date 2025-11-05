import sys
import os
import json
import shutil
import numpy as np
import faiss
from sklearn.cluster import KMeans
from sklearn.ensemble import IsolationForest

IMG_EMB_FILE = "/home/team6/tag-search/PoC/source/embedding_vectors.npy"
FILENAMES_FILE = "/home/team6/tag-search/PoC/source/embedding_ids.json"
ANNOTATION_FILE_1 = "/home/team6/tag-search/PoC/source/tag_info.json"
ANNOTATION_FILE_2 = "/home/team6/tag-search/PoC/source/image_to_words.json"

IMAGE_BASE_PATH = "/home/team6/real_image_testset"
RESULT_BASE_PATH = "/home/team6/tag-search/PoC/results/recommend_with_rep_vec"

k = 3                  # K-Means 클러스터 개수
outlier_fraction = 0.05 # 이상치(outlier)로 간주할 데이터의 비율 (5%)

required_files = [IMG_EMB_FILE, FILENAMES_FILE, ANNOTATION_FILE_1, ANNOTATION_FILE_2]
for fpath in required_files:
    if not os.path.exists(fpath):
        print(f"오류: 필수 파일 '{fpath}'를 찾을 수 없습니다. 스크립트를 종료합니다.")
        sys.exit(1)

# 두 종류의 어노테이션 파일을 하나로 병합
img_to_cats = {}
with open(ANNOTATION_FILE_1, "r") as f:
    img_to_cats.update(json.load(f))

with open(ANNOTATION_FILE_2, "r") as f:
    data_to_merge = json.load(f)
    for fname, tags in data_to_merge.items():
        existing_tags = set(img_to_cats.get(fname, []))
        existing_tags.update(tags)
        img_to_cats[fname] = sorted(list(existing_tags))

# 임베딩 벡터와 파일명 목록 로딩
images = np.load(IMG_EMB_FILE)
with open(FILENAMES_FILE, "r") as f:
    filenames_list = json.load(f)
filenames = np.array(filenames_list)

# 전체 카테고리(태그) 목록 생성
all_categories = sorted(list(set(cat for tags in img_to_cats.values() for cat in tags)))

# --- 함수 정의 ---
def compute_vectors_with_outlier_handling(category_name, k, contamination):
    """특정 카테고리에 속한 이미지들로부터 이상치를 포함한 대표 벡터들을 계산합니다."""
    selected_vecs = [vec for fname, vec in zip(filenames, images) if category_name in img_to_cats.get(fname, [])]
    selected_vecs = np.array(selected_vecs)

    if len(selected_vecs) < 10:
        return selected_vecs

    iso_forest = IsolationForest(contamination=contamination, random_state=42)
    preds = iso_forest.fit_predict(selected_vecs)
    
    outlier_vecs = selected_vecs[preds == -1]
    inlier_vecs = selected_vecs[preds == 1]
    
    kmeans_centers = np.array([])
    if len(inlier_vecs) >= k:
        kmeans = KMeans(n_clusters=k, random_state=42, n_init='auto')
        kmeans.fit(inlier_vecs)
        kmeans_centers = kmeans.cluster_centers_
    elif len(inlier_vecs) > 0:
        kmeans_centers = inlier_vecs
        
    final_representatives = []
    if len(outlier_vecs) > 0:
        final_representatives.append(outlier_vecs)
    if len(kmeans_centers) > 0:
        final_representatives.append(kmeans_centers)

    return np.vstack(final_representatives) if final_representatives else np.array([])

def build_faiss_index(representative_vectors: np.ndarray):
    """대표 벡터들로부터 Faiss 검색 인덱스를 생성합니다."""
    d = representative_vectors.shape[1]
    norms = np.linalg.norm(representative_vectors, axis=1, keepdims=True)
    norms[norms == 0] = 1e-10 # 0으로 나누는 것을 방지
    normalized_vectors = (representative_vectors / norms).astype('float32')
    
    index = faiss.IndexFlatL2(d)
    index.add(normalized_vectors)
    return index

def find_images_by_top_k_tags(search_categories, faiss_index, rep_vec_cats, cand_vecs, cand_fnames):
    """태그들을 입력받아 관련된 이미지 파일명 리스트를 반환합니다."""
    k_to_find = len(search_categories)
    norms = np.linalg.norm(cand_vecs, axis=1, keepdims=True)
    norms[norms == 0] = 1e-10
    normalized_candidates = (cand_vecs / norms).astype('float32')
    
    _distances, top_k_indices = faiss_index.search(normalized_candidates, k_to_find)
    
    retrieved_fnames = []
    search_categories_set = set(search_categories)
    for i, fname in enumerate(cand_fnames):
        top_k_categories = {rep_vec_cats[idx] for idx in top_k_indices[i]}
        if search_categories_set.issubset(top_k_categories):
            retrieved_fnames.append(fname)
    return retrieved_fnames

def copy_images_to_folder(src_paths, dest_folder):
    """찾은 이미지들을 지정된 폴더에 복사합니다."""
    os.makedirs(dest_folder, exist_ok=True)
    for src_path in src_paths:
        if not os.path.exists(src_path):
            print(f"경고: 원본 이미지 '{src_path}'를 찾을 수 없어 복사를 건너뜁니다.")
            continue
        dest_path = os.path.join(dest_folder, os.path.basename(src_path))
        shutil.copy2(src_path, dest_path)

def find_top_k_tags_for_image(image_id, k_neighbors, faiss_index, rep_vec_cats):
    """
    주어진 이미지 ID에 대해 가장 가까운 대표 벡터 k개를 찾아 연관 태그를 출력합니다.
    """
    try:
        image_idx = filenames_list.index(image_id)
        target_vector = images[image_idx]
    except ValueError:
        print(f"오류: 이미지 ID '{image_id}'를 파일 목록에서 찾을 수 없습니다.")
        return

    # Faiss 검색을 위해 벡터 정규화 및 형태 변환 (2D 배열로)
    target_vector_2d = target_vector.reshape(1, -1).astype('float32')
    norm = np.linalg.norm(target_vector_2d)
    if norm == 0: 
        norm = 1e-10
    normalized_target = target_vector_2d / norm

    # Faiss 인덱스에서 가장 가까운 k+alpha개 검색
    excluded_cats = set(img_to_cats[image_id])
    k_with_buffer = k_neighbors + len(excluded_cats)
    if k_with_buffer > faiss_index.ntotal:
        k_with_buffer = faiss_index.ntotal
        
    distances, indices = faiss_index.search(normalized_target, k_with_buffer)

    print(f"\n'{image_id}' 이미지와 가장 연관성 높은 태그 TOP {k_neighbors}:")
    final_results = []
    for i in range(k_with_buffer):
        if len(final_results) == k_neighbors:
            break
        found_index = indices[0][i]
        found_category = rep_vec_cats[found_index]
        found_distance = distances[0][i]
        if found_category not in excluded_cats:
            final_results.append({
                "category": found_category,
                "distance": found_distance
            })
            
    for i, result in enumerate(final_results):
        print(f"  {i+1}. 태그: {result['category']} (유사도 거리: {result['distance']:.4f})")


# --- 메인 실행 블록 ---
if __name__ == "__main__":
    # 1. 입력 인자에 따라 실행 모드 분기
    if len(sys.argv) < 1:
        print("\nUsage: python tag_recommend_with_rep_vec.py <image_id.jpg>")
        sys.exit(1)

    # 2. 모든 카테고리에 대한 대표 벡터 생성
    print("\n모든 태그에 대한 대표 벡터를 생성 중입니다 (이상치 포함)...")
    all_representative_vectors = []
    representative_vector_categories = []
    for cat in all_categories:
        new_vectors = compute_vectors_with_outlier_handling(cat, k=k, contamination=outlier_fraction)
        if len(new_vectors) > 0:
            all_representative_vectors.extend(new_vectors)
            representative_vector_categories.extend([cat] * len(new_vectors))
    all_representative_vectors = np.array(all_representative_vectors)
    
    # 3. Faiss 인덱스 빌드
    print("대표 벡터들로 Faiss 검색 인덱스를 빌드 중입니다...")
    faiss_index = build_faiss_index(all_representative_vectors)
    print("--- 시스템 준비 완료 ---")
    
    if len(sys.argv) != 2:
        print("사용법: python your_script.py <image_id.jpg>")
        sys.exit(1)
    image_id_to_analyze = sys.argv[1]
    find_top_k_tags_for_image(
        image_id=image_id_to_analyze, 
        k_neighbors=5,
        faiss_index=faiss_index,
        rep_vec_cats=representative_vector_categories
    )