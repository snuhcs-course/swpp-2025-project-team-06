import torch
import os
import json
import numpy as np
from transformers import CLIPProcessor, CLIPModel
from peft import PeftModel
from sklearn.metrics.pairwise import cosine_similarity
import shutil
import sys

DEVICE = "cuda" if torch.cuda.is_available() else "cpu"
MODEL_ID = "openai/clip-vit-large-patch14"
ADAPTER_BASE_PATH = "/home/team6/tag-search/user_adapters"
EMBEDDINGS_VECTORS_FILE_ADAPTED = "/home/team6/tag-search/PoC/source/embedding_vectors.npy"
EMBEDDINGS_IDS_FILE_ADAPTED = "/home/team6/tag-search/PoC/source/embedding_ids.json"

SOURCE_IMAGE_DIR = "/home/team6/real_image_testset" 
RESULTS_BASE_DIR = "/home/team6/tag-search/PoC/results/search/search_results"

def load_model_with_adapter(userName):
    """LoRA 어댑터가 적용된 모델을 로드합니다."""
    print(f"'{userName}' 어댑터가 적용된 모델을 로드합니다...")
    user_adapter_path = os.path.join(ADAPTER_BASE_PATH, userName)
    if not os.path.exists(user_adapter_path):
        raise FileNotFoundError(f"'{userName}' 사용자의 어댑터를 찾을 수 없습니다.")

    base_model = CLIPModel.from_pretrained(MODEL_ID).to(DEVICE)
    inference_model = PeftModel.from_pretrained(base_model, user_adapter_path).to(DEVICE)
    inference_model.eval()
    return inference_model

def load_base_model():
    """어댑터 없이 기본 CLIP 모델을 로드합니다."""
    print("어댑터 없이 기본 CLIP 모델을 로드합니다...")
    model = CLIPModel.from_pretrained(MODEL_ID).to(DEVICE)
    model.eval()
    return model

# [수정됨] 새로운 데이터 로딩 함수
def load_embeddings(vectors_path, ids_path):
    """
    저장된 임베딩 .npy 파일과 id .json 파일을 로드하고
    NumPy 배열과 리스트로 반환합니다.
    """
    print(f"'{vectors_path}'와 '{ids_path}'에서 임베딩 데이터를 로드합니다...")
    if not os.path.exists(vectors_path) or not os.path.exists(ids_path):
        raise FileNotFoundError(f"필수 임베딩 파일({vectors_path} 또는 {ids_path})을 찾을 수 없습니다.")

    # 벡터와 ID를 각각의 파일에서 로드
    vectors = np.load(vectors_path)
    with open(ids_path, 'r', encoding='utf-8') as f:
        ids = json.load(f)

    # 데이터 무결성 검사
    if len(ids) != vectors.shape[0]:
        raise ValueError(
            f"임베딩 벡터의 수({vectors.shape[0]})와 ID의 수({len(ids)})가 일치하지 않습니다."
        )

    print(f"총 {len(ids)}개의 임베딩 로드 완료.")
    return ids, vectors

def embed_query_text(model, processor, text):
    """주어진 텍스트 쿼리를 임베딩합니다."""
    with torch.no_grad():
        if not text:
            raise ValueError("검색할 텍스트를 입력해야 합니다.")
        inputs = processor(text=text, return_tensors="pt").to(DEVICE)
        query_vector = model.get_text_features(**inputs)
    
    return query_vector.cpu().numpy()

def search(query_vector, all_vectors, all_ids, top_k=5):
    """쿼리 벡터와 가장 유사한 top_k개의 결과를 찾습니다."""
    sim_scores = cosine_similarity(query_vector.reshape(1, -1), all_vectors)[0]
    top_indices = np.argsort(sim_scores)[::-1][:top_k]
    results = [(all_ids[i], sim_scores[i]) for i in top_indices]
    return results

if __name__ == "__main__":
    # --- 커맨드 라인 인자 확인 ---
    USER_NAME = "my_real_data_model" 

    # --- ✍️ 텍스트 쿼리 설정 ---
    QUERY_TEXT = sys.argv[1]
    
    print(f"--- 검색어: '{QUERY_TEXT}' ---")

    try:
        # 1. 모델과 프로세서 로드
        processor = CLIPProcessor.from_pretrained(MODEL_ID)
        
        model = load_model_with_adapter(USER_NAME)

        # 2. 저장된 임베딩 데이터베이스 로드 ([수정됨] 호출 방식 변경)
        db_ids, db_vectors = load_embeddings(
            vectors_path=EMBEDDINGS_VECTORS_FILE_ADAPTED, 
            ids_path=EMBEDDINGS_IDS_FILE_ADAPTED
        )

        # 3. 텍스트 쿼리를 벡터로 변환 (임베딩)
        print("\n텍스트 쿼리를 임베딩합니다...")
        query_vec = embed_query_text(model, processor, text=QUERY_TEXT)

        # 4. 유사도 검색 실행
        print("유사도 검색을 시작합니다...")
        top_results = search(query_vec, db_vectors, db_ids, top_k=20)

        # 5. 결과 출력
        print("\n--- 검색 결과 Top 20 ---")
        for i, (img_id, score) in enumerate(top_results):
            print(f"Rank {i+1}: {img_id} (유사도: {score:.4f})")
        
        # 6. 검색 결과를 폴더에 이미지로 저장
        if not top_results:
            print("\n검색 결과가 없습니다.")
        else:
            sanitized_query = QUERY_TEXT.replace(" ", "_").replace("?", "").replace("/", "")
            result_dir = os.path.join(RESULTS_BASE_DIR, sanitized_query)
            os.makedirs(result_dir, exist_ok=True)
            print(f"\n결과를 '{result_dir}' 폴더에 저장합니다...")

            copied_count = 0
            for img_id, score in top_results:
                source_path = os.path.join(SOURCE_IMAGE_DIR, img_id)
                dest_path = os.path.join(result_dir, img_id)
                
                if os.path.exists(source_path):
                    shutil.copy2(source_path, dest_path)
                    copied_count += 1
                else:
                    print(f"경고: 원본 파일을 찾을 수 없습니다 - {source_path}")
            
            print(f"총 {copied_count}개의 이미지를 결과 폴더에 복사했습니다.")
            
    except Exception as e:
        print(f"\n오류 발생: {e}")