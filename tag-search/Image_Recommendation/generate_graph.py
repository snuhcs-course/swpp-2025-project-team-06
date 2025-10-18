import json
import numpy as np
import networkx as nx
import re
from tqdm import tqdm
from collections import Counter

# --- 설정 ---
NPY_FILE_PATH = "/home/team6/tag-search/PoC/source/caption-real-image.npy"
OUTPUT_GRAPH_FILE = "/home/team6/tag-search/PoC/source/image_word_graph_weighted.graphml"
OUTPUT_WORDS_JSON_PATH = "/home/team6/tag-search/PoC/source/image_to_words.json"

# stop words which cannot be node of graph
STOP_WORDS = {
    'a', 'an', 'the', 'in', 'on', 'of', 'at', 'for', 'with', 'by', 'about',
    'is', 'are', 'was', 'were', 'it', 'this', 'that', 'and', 'or', 'but',
    'photo', 'picture', 'image'
}

# caption(sentence) -> word list
def preprocess_text(text):
    text = text.lower() # unified in lowercase
    cleaned_text = re.sub(r'[^a-zA-Z\s]', '', text)
    words = cleaned_text.split() # split to words
    if not words:
        return []

    deduplicated_words = [] # remove consecutive duplicates
    last_word = None
    for word in words:
        if word != last_word:
            deduplicated_words.append(word)
            last_word = word

    final_words = [
        word for word in deduplicated_words
        if word not in STOP_WORDS and len(word) > 1 # filter stop words and short words
    ]
    return final_words

# data loading
print(f"Load caption from '{NPY_FILE_PATH}'...")
all_captions = np.load(NPY_FILE_PATH, allow_pickle=True).item()
print(f"Found {len(all_captions)} photos")

# generate bipartite graph
B = nx.Graph()
image_to_words_dict = {} # for store json
print("Start creating weighted bipartite graph and word list...")

for image_name, captions_list in tqdm(all_captions.items(), desc="graph & words generation"):
    B.add_node(image_name, bipartite=0)

    all_words_for_image = []
    for caption in captions_list:
        words = preprocess_text(caption)
        all_words_for_image.extend(words)
    
    word_counts = Counter(all_words_for_image)
    
    image_to_words_dict[image_name] = list(word_counts.keys()) # store image-word dict as json # word does not repeat

    for word, count in word_counts.items():
        if not B.has_node(word):
            B.add_node(word, bipartite=1)
        B.add_edge(image_name, word, weight=count)

print("Complete generating graph and word list.")

# save image-word dict as json
print(f"\nSave image-word dict to '{OUTPUT_WORDS_JSON_PATH}'...")
with open(OUTPUT_WORDS_JSON_PATH, 'w', encoding='utf-8') as f:
    json.dump(image_to_words_dict, f, ensure_ascii=False, indent=4)
print("Saved successfully")

# graph information
image_nodes = {n for n, d in B.nodes(data=True) if d['bipartite'] == 0}
word_nodes = {n for n, d in B.nodes(data=True) if d['bipartite'] == 1}

print("\n--- Graph Information ---")
print(f"Total number of nodes: {B.number_of_nodes()}")
print(f"  - Image nodes: {len(image_nodes)}")
print(f"  - Word nodes: {len(word_nodes)}")
print(f"Total number of edges: {B.number_of_edges()}")

# save graph
print(f"\nSave generated graph to '{OUTPUT_GRAPH_FILE}'...")
nx.write_graphml(B, OUTPUT_GRAPH_FILE)
print("Saved successfully")