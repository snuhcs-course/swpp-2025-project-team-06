from sentence_transformers import SentenceTransformer
from PIL import Image
from pathlib import Path
from sklearn.metrics.pairwise import cosine_similarity
import torch.nn.functional as F
import numpy as np
import torch
import shutil
import io
import time

MODEL_NAME = "clip-ViT-B-32"
SRC_DIR = Path("/home/team6/real_image_testset")
DST_DIR = Path("/home/team6/real_image_grouped")
DST_DIR.mkdir(parents=True, exist_ok=True)

SIM_THRESHOLD = 0.85  # experimental value
WINDOW_SIZE = 5
DOWNGRADE_QUALITY = 25  # 0~100
BATCH_SIZE = 32

def downgrade_image_in_memory(img_path, quality=DOWNGRADE_QUALITY):
    img = Image.open(img_path).convert("RGB")
    buffer = io.BytesIO()
    img.save(buffer, format="JPEG", quality=quality)
    buffer.seek(0)
    downgraded = Image.open(buffer)
    return downgraded

def main():
    model = SentenceTransformer(MODEL_NAME, device="cuda").half()

    embeddings_window = []

    # sorting
    extensions = ["jpg", "jpeg", "png"]
    image_paths = sorted(
        path for ext in extensions for path in SRC_DIR.glob(f"*.{ext}")
    )
    
    print("=== Start ===")
    st_group=time.time()
    
    # group with batching
    for i in range(0, len(image_paths), BATCH_SIZE):
        batch_paths = image_paths[i:i+BATCH_SIZE] # batching
        imgs = [downgrade_image_in_memory(p) for p in batch_paths] # downgrade

        with torch.no_grad():
            embs = model.encode(imgs, convert_to_tensor=True, batch_size=BATCH_SIZE) # encode
        
        for emb, img_path in zip(embs, batch_paths):
            should_copy = True
            if len(embeddings_window) > 0: # compute similarity
                sims = F.cosine_similarity(emb.unsqueeze(0), torch.stack(embeddings_window))
                max_sim = sims.max().item()
                if max_sim >= SIM_THRESHOLD:
                    should_copy = False

            if should_copy: # copy
                # shutil.copy2(img_path, DST_DIR / img_path.name) # already copied
                pass

            embeddings_window.append(emb)
            if len(embeddings_window) > WINDOW_SIZE:
                embeddings_window.pop(0)
                
    et_group=time.time()

    print("\n=== End ===")
    print(f"time taken: {et_group - st_group:.2f} seconds")
    print(f"copied {len(list(DST_DIR.glob('*.jpg')))} photos")

if __name__ == "__main__":
    main()
