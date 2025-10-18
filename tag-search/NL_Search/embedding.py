import torch
import os
import json
import glob
import numpy as np
from PIL import Image
from datasets import Dataset
from transformers import CLIPProcessor, CLIPModel
from peft import LoraConfig, get_peft_model, PeftModel
from torch.utils.data import DataLoader
from tqdm import tqdm

DEVICE = "cuda" if torch.cuda.is_available() else "cpu"
MODEL_ID = "openai/clip-vit-large-patch14"
ADAPTER_BASE_PATH = "/home/team6/tag-search/user_adapters"

try:
    BASE_MODEL = CLIPModel.from_pretrained(MODEL_ID).to(DEVICE)
    PROCESSOR = CLIPProcessor.from_pretrained(MODEL_ID)
    os.makedirs(ADAPTER_BASE_PATH, exist_ok=True)
except Exception as e:
    print(f"모델 로드 중 오류 발생: {e}")
    exit()


def load_training_data(image_dir, metadata_path):
    """지정된 폴더와 JSON 파일에서 학습용 이미지와 캡션을 로드합니다."""
    if not os.path.exists(metadata_path):
        raise FileNotFoundError(f"학습용 캡션 파일을 찾을 수 없습니다: {metadata_path}")
    
    with open(metadata_path, 'r', encoding='utf-8') as f:
        metadata = json.load(f)
    
    image_set, caption_set = [], []
    for item in metadata:
        print(item['file_name'])
        image_path = os.path.join(image_dir, item['file_name'])
        if os.path.exists(image_path):
            try:
                # 학습 데이터의 양이 비교적 적다고 가정 후 메모리에 로드
                # 학습 데이터가 매우 크다면 수정이 필요합니다.
                with Image.open(image_path) as image:
                    image_set.append(image.convert("RGB"))
                    caption_set.append(item['text'])
            except Exception as e:
                print(f"경고: {image_path} 이미지 처리 중 오류 발생하여 건너뜁니다 - {e}")
        else:
            print(f"경고: 학습용 이미지를 찾을 수 없습니다 - {image_path}")
            
    print(f"총 {len(image_set)}개의 학습용 이미지와 캡션을 로드했습니다.")
    return image_set, caption_set

def get_embedding_image_paths(image_dir):
    """지정된 폴더에서 임베딩할 모든 이미지의 '경로'와 '파일명(ID)'을 가져옵니다."""
    if not os.path.isdir(image_dir):
        raise FileNotFoundError(f"임베딩용 이미지 폴더를 찾을 수 없습니다: {image_dir}")

    image_paths = []
    supported_formats = ('*.png', '*.jpg', '*.jpeg')
    for fmt in supported_formats:
        image_paths.extend(glob.glob(os.path.join(image_dir, fmt)))
    
    # 파일명(ID)만 추출
    image_ids = [os.path.basename(p) for p in image_paths]
    
    print(f"총 {len(image_paths)}개의 임베딩 대상 이미지 경로를 찾았습니다.")
    return image_paths, image_ids

# 어댑터 학습 및 임베딩 함수
def train_and_save_adapter(userName, image_set, caption_set, num_epochs=50, lr=1e-4, batch_size=32):
    user_adapter_path = os.path.join(ADAPTER_BASE_PATH, userName)
    
    if os.path.exists(user_adapter_path):
        # 기본 모델에 기존에 저장된 어댑터를 로드합니다.
        lora_model = PeftModel.from_pretrained(BASE_MODEL, user_adapter_path, is_trainable=True)
    else:
        # 기존 어댑터가 없으면 새로 설정하고 생성합니다.
        lora_config = LoraConfig(r=16, lora_alpha=32, target_modules=["q_proj", "v_proj"])
        lora_model = get_peft_model(BASE_MODEL, lora_config)
    # -----------------------------------------

    lora_model = lora_model.to(DEVICE) # 모델을 지정된 디바이스로 보냅니다.
    lora_model.train() # 모델을 훈련 모드로 설정합니다.

    # 데이터셋과 데이터로더 준비
    user_dataset = Dataset.from_dict({"image": image_set, "text": caption_set})
    def collate_fn(examples):
        return PROCESSOR(
            text=[e["text"] for e in examples], images=[e["image"] for e in examples],
            return_tensors="pt", padding=True, truncation=True
        ).to(DEVICE)
    data_loader = DataLoader(user_dataset, batch_size=batch_size, collate_fn=collate_fn, shuffle=True)
    
    # 옵티마이저와 손실 함수
    optimizer = torch.optim.AdamW(lora_model.parameters(), lr=lr)
    loss_fn = torch.nn.CrossEntropyLoss()

    # 학습 루프
    for epoch in range(num_epochs):
        for batch in tqdm(data_loader, desc=f"Epoch {epoch + 1}/{num_epochs}"):
            optimizer.zero_grad()
            outputs = lora_model(**batch)
            logits_per_image = outputs.logits_per_image
            logits_per_text = outputs.logits_per_text
            batch_size = logits_per_image.shape[0]
            labels = torch.arange(batch_size).to(DEVICE)
            loss_images = loss_fn(logits_per_image, labels)
            loss_texts = loss_fn(logits_per_text, labels)
            loss = (loss_images + loss_texts) / 2.0
            loss.backward()
            optimizer.step()
    
    # 학습이 끝나면 다시 저장 (기존 어댑터 위에 덮어쓰기)
    lora_model.save_pretrained(user_adapter_path)
    print(f"'{userName}' 사용자의 어댑터 추가 학습 완료 및 저장: {user_adapter_path}")
    print("="*60)
    lora_model.unload()

def embed_images_with_adapter(userName: str, image_paths: list) -> np.ndarray:
    """지정된 경로의 이미지들을 하나씩 불러와 벡터로 변환합니다."""
    user_adapter_path = os.path.join(ADAPTER_BASE_PATH, userName)
    if not os.path.exists(user_adapter_path):
        raise FileNotFoundError(f"'{userName}' 사용자의 어댑터를 찾을 수 없습니다.")

    # 모델은 한 번만 로드
    inference_model = PeftModel.from_pretrained(BASE_MODEL, user_adapter_path).to(DEVICE)
    inference_model.eval()
    
    all_vectors = []
    with torch.no_grad():
        # 이미지 경로 리스트를 순회
        for path in tqdm(image_paths, desc="이미지 임베딩 중"):
            try:
                # 루프 안에서 이미지를 열고, 처리 후 자동으로 닫음
                with Image.open(path).convert("RGB") as image:
                    inputs = PROCESSOR(images=image, return_tensors="pt").to(DEVICE)
                    image_features = inference_model.get_image_features(**inputs)
                    all_vectors.append(image_features.cpu().numpy())
            except Exception as e:
                print(f"경고: {path} 처리 중 오류 발생하여 건너뜁니다 - {e}")

    print("이미지 임베딩 완료.")
    print("="*60)
    inference_model.unload() # 어댑터 언로드
    return np.concatenate(all_vectors, axis=0) if all_vectors else np.array([])


# 실행 블록
if __name__ == "__main__":
    # --- 설정 ---
    TRAIN_IMAGE_DIR = "/home/team6/real_image_train"
    TRAIN_CAPTION_FILE = os.path.join(TRAIN_IMAGE_DIR, "caption_train.json")
    TEST_IMAGE_DIR = "/home/team6/real_image_testset"
    USER_NAME = "my_real_data_model"
    OUTPUT_VECTORS_PATH = "/home/team6/tag-search/PoC/source/embedding_vectors.npy"
    OUTPUT_IDS_PATH = "/home/team6/tag-search/PoC/source/embedding_ids.json"

    try:
        # 1. 학습 데이터 로드
        train_images, train_captions = load_training_data(TRAIN_IMAGE_DIR, TRAIN_CAPTION_FILE)
        
        if not train_images:
            print("훈련할 이미지가 없습니다. 스크립트를 종료합니다.")
        else:
            # 2. 어댑터 학습 실행
            train_and_save_adapter(
                userName=USER_NAME,
                image_set=train_images,
                caption_set=train_captions
            )

            # 3. 임베딩할 이미지 '경로'와 'ID' 로드 (메모리 사용량 적음)
            embedding_image_paths, embedding_image_ids = get_embedding_image_paths(TEST_IMAGE_DIR)

            if not embedding_image_paths:
                print("임베딩할 이미지가 없습니다.")
            else:
                # 4. 학습된 어댑터로 임베딩 실행 (경로 리스트 전달)
                vectors = embed_images_with_adapter(
                    userName=USER_NAME,
                    image_paths=embedding_image_paths
                )

                if vectors.size == 0:
                    print("임베딩된 벡터가 없습니다. 저장 단계를 건너뜁니다.")
                else:
                    # 5. 결과 확인
                    print("최종 임베딩 결과:")
                    print(f" - 총 {vectors.shape[0]}개의 이미지가 임베딩되었습니다.")
                    print(f" - 각 벡터의 차원(크기)은 {vectors.shape[1]}입니다.")
                    
                    # 6. ID와 벡터를 매핑하여 JSON 파일로 저장
                    print(f"\n임베딩 벡터를 '{OUTPUT_VECTORS_PATH}' 파일로 저장합니다...")
                    np.save(OUTPUT_VECTORS_PATH, vectors)
                    
                    print(f"이미지 ID 목록을 '{OUTPUT_IDS_PATH}' 파일로 저장합니다...")
                    with open(OUTPUT_IDS_PATH, 'w', encoding='utf-8') as f:
                        json.dump(embedding_image_ids, f, ensure_ascii=False, indent=4)
                    
                    print("저장 완료!")

        print("\n모든 작업이 성공적으로 완료되었습니다.")

    except Exception as e:
        print(f"\n스크립트 실행 중 오류 발생: {e}")