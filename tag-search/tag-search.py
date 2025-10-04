import numpy as np
import json
import sys
import random
from sklearn.metrics.pairwise import cosine_similarity
from sentence_transformers import SentenceTransformer

IMG_EMB_FILE = "images.npy"        # (N, D)
FILENAMES_FILE = "filenames.npy"   # (N,)
ANNOTATION_FILE = "instances_val2017.json"

TEXT_MODEL_NAME = "sentence-transformers/clip-ViT-B-32-multilingual-v1"

# load annotation
with open(ANNOTATION_FILE, "r") as f:
    coco_data = json.load(f)

cat_map = {cat["id"]: cat["name"] for cat in coco_data["categories"]}

# file_name -> categories mapping
img_to_cats = {}
for ann in coco_data["annotations"]:
    img_id = ann["image_id"]
    image_info = next(img for img in coco_data["images"] if img["id"] == img_id)
    fname = image_info["file_name"]
    cat_name = cat_map[ann["category_id"]]
    img_to_cats.setdefault(fname, []).append(cat_name)

# load embeddings
images = np.load(IMG_EMB_FILE)       # (N, D)
filenames = np.load(FILENAMES_FILE)  # (N,)

# text embedding model
text_model = SentenceTransformer(TEXT_MODEL_NAME)

# build 1~80 mapping
all_categories = sorted(set(cat_map.values()))
id80_to_category = {i+1: cat for i, cat in enumerate(all_categories)}  # 1~80 → name
category_to_id80 = {cat: i for i, cat in id80_to_category.items()}


def search_by_text(category_name, top_k):
    text_emb = text_model.encode([category_name])
    sims = cosine_similarity(text_emb, images).flatten()
    top_idxs = sims.argsort()[::-1][:top_k]
    top_fnames = [filenames[i] for i in top_idxs]
    top_sims = [sims[i] for i in top_idxs]

    correct = 0
    output = []
    for fname, sim in zip(top_fnames, top_sims):
        real_cats = img_to_cats.get(fname, [])
        if category_name in real_cats:
            output.append(f"{fname:<20} | sim={sim:.3f}")
            correct += 1
        else:
            output.append(f"{fname:<20} | sim={sim:.3f}   X")

    accuracy = correct / top_k
    return output, correct, accuracy


# return center vector & indices of images used to compute it
def compute_center_from_sampling(category_name, p=1.0):
    selected_vecs = []
    selected_idxs = []
    for i, (fname, vec) in enumerate(zip(filenames, images)):
        cats = img_to_cats.get(fname, [])
        if category_name in cats and random.random() < p:
            selected_vecs.append(vec)
            selected_idxs.append(i)

    if len(selected_vecs) == 0:
        raise ValueError(f"No images selected for category '{category_name}' with p={p}")

    center_vec = np.mean(selected_vecs, axis=0)
    return center_vec, selected_idxs


def search_by_center(center_vec, category_name, exclude_idxs=None, top_k=20):
    if exclude_idxs is None:
        exclude_idxs = []

    # search for non-tagged pics
    candidate_idxs = [i for i in range(len(filenames)) if i not in exclude_idxs]
    candidate_vectors = images[candidate_idxs]
    candidate_fnames = [filenames[i] for i in candidate_idxs]

    sims = cosine_similarity(center_vec.reshape(1, -1), candidate_vectors).flatten()
    top_idxs = sims.argsort()[::-1][:top_k]
    top_fnames = [candidate_fnames[i] for i in top_idxs]
    top_sims = [sims[i] for i in top_idxs]

    correct = 0
    output = []
    for fname, sim in zip(top_fnames, top_sims):
        real_cats = img_to_cats.get(fname, [])
        if category_name in real_cats:
            output.append(f"{fname:<20} | sim={sim:.3f}")
            correct += 1
        else:
            output.append(f"{fname:<20} | sim={sim:.3f}   X")

    accuracy = correct / top_k
    return output, correct, accuracy


if __name__ == "__main__":
    if len(sys.argv) < 4:
        print("Usage: python script.py <category_id:1-80> <p:0-1> <top_k>")
        sys.exit(1)

    cat_id80 = int(sys.argv[1])
    p = float(sys.argv[2])
    top_k = int(sys.argv[3])

    if not (1 <= cat_id80 <= 80):
        raise ValueError(f"Category id {cat_id80} is not in 1~80")

    target_category = id80_to_category[cat_id80]

    # total image in target category
    total_count = sum(target_category in cats for cats in img_to_cats.values())
    print(f"Target category: '{target_category}' (mapped id={cat_id80})")
    print(f"Total images with this category: {total_count}")
    print(f"Sampling prob p={p} | top_k={top_k}\n")

    # natural language embedding search
    print("=== Natural language embedding search ===")
    print(": Searching top-K images most similar to the category name using text embedding.")
    results, correct, accuracy = search_by_text(target_category, top_k=top_k)
    for line in results:
        print(line)
    print(f"\n[Text] Correct matches: {correct}/{top_k} | Accuracy: {accuracy:.2f}\n")

    # center embedding search
    print("=== Sampled center embedding search ===")
    print(": Searching top-K images most similar to the sampled center vector of images, excluding the sampled ones.")
    center_vec, exclude_idxs = compute_center_from_sampling(target_category, p=p)
    results, correct, accuracy = search_by_center(center_vec, target_category, exclude_idxs=exclude_idxs, top_k=top_k)
    for line in results:
        print(line)
    print(f"\n[Center] Correct matches: {correct}/{top_k} | Accuracy: {accuracy:.2f}")
