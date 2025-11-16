import torch
from torch.utils.data import Dataset, DataLoader
from transformers import BlipProcessor, BlipForConditionalGeneration
from PIL import Image
import time
import os
from tqdm import tqdm

IMG_DIR = "/home/team6/real_image_grouped"
OUTPUT_CAPTION_FILE = "/home/team6/tag-search/PoC/source/caption-real-image.npy"
BATCH_SIZE = 8 # optimized value
NUM_WORKERS = 8 # optimized value

device = "cuda" if torch.cuda.is_available() else "cpu"
processor = BlipProcessor.from_pretrained("Salesforce/blip-image-captioning-base")
model = BlipForConditionalGeneration.from_pretrained("Salesforce/blip-image-captioning-base").to(device)
model.half()  # FP16

# optimize 1 - torch.compile()
print("Compiling the model... (This may take a moment)")
model = torch.compile(model) 
model.eval()

# image list
img_files = sorted([
    os.path.join(IMG_DIR, f) for f in os.listdir(IMG_DIR)
    if f.lower().endswith((".jpg", ".jpeg", ".png"))
])
print(f"processing {len(img_files)} photos for captioning...")

# optimize 2 - for parallelism of data loading, define dataset class
class ImageCaptioningDataset(Dataset):
    def __init__(self, image_paths, processor):
        self.image_paths = image_paths
        self.processor = processor

    def __len__(self):
        return len(self.image_paths)

    def __getitem__(self, idx):
        path = self.image_paths[idx]
        try:
            image = Image.open(path).convert("RGB") # do not downgrade; downgrading does not really works effectively
            return {'image': image, 'path': path}
        except Exception as e:
            print(f"[Warning] Failed to load image {path}: {e}")
            return {'image': None, 'path': path}

# batch collate function
def collate_fn(batch):
    batch = [b for b in batch if b['image'] is not None]
    if not batch:
        return None
    
    images = [b['image'] for b in batch]
    paths = [b['path'] for b in batch]
    
    inputs = processor(images=images, return_tensors="pt", padding=True)
    return {'inputs': inputs, 'paths': paths}

# data set, data loader
dataset = ImageCaptioningDataset(img_files, processor)
data_loader = DataLoader(
    dataset,
    batch_size=BATCH_SIZE,
    shuffle=False,
    num_workers=NUM_WORKERS,
    collate_fn=collate_fn,
    pin_memory=True # accelerates data transfer to GPU
)

# === main logic ===
all_captions = {}
start_time = time.time()

for batch_data in tqdm(data_loader):
    if batch_data is None:
        continue
        
    inputs = batch_data['inputs'].to(device, non_blocking=True) # async copy
    batch_paths = batch_data['paths']

    try:
        with torch.no_grad():
            outputs = model.generate(
                **inputs,
                max_new_tokens=20,
                do_sample=True,
                top_k=50,
                top_p=0.95,
                temperature=1.0,
                num_return_sequences=5
            )

        captions = [processor.decode(o, skip_special_tokens=True) for o in outputs]
        grouped = [captions[i:i+5] for i in range(0, len(captions), 5)] # (batch × num_return_sequences) results -> grouped caption by image

        for path, caps in zip(batch_paths, grouped):
            all_captions[os.path.basename(path)] = caps
            
    except Exception as e:
        print(f"[ERROR] on batch starting with {os.path.basename(batch_paths[0])}: {e}")

# save as npy
# np.save(OUTPUT_CAPTION_FILE, all_captions) # already saved

end_time = time.time()
print(f"\ntime taken: {end_time - start_time:.2f}s")
images_per_second = len(img_files) / (end_time - start_time)
print(f"Processing speed: {images_per_second:.2f} images/sec")
print(f"Results saved to '{OUTPUT_CAPTION_FILE}'.")