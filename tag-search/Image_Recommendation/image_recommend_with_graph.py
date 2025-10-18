import numpy as np
import networkx as nx
from tqdm import tqdm
import json

GRAPH_FILE_PATH = "/home/team6/tag-search/PoC/source/image_word_graph_weighted.graphml"
JSON_FILE_PATH = "/home/team6/tag-search/PoC/source/tag_info.json"
TARGET_GROUP_NAME = "board"

# image recommendation with Adamic/Adar & weighted RWR
def recommend_images(B, group_A, image_nodes, top_n=10, alpha=0.5):
    print(f"\n--- Recommend system (alpha={alpha}) ---")
    candidates = image_nodes - set(group_A)
    if not candidates:
        print("No images to recommend")
        return []

    print("1/4: Weighted Rooted PageRank score is being calculated...")
    rwr_scores = nx.pagerank(B, personalization={node: 1 for node in group_A}, weight='weight')

    print("2/4: Adamic/Adar score is being calculated...")
    aa_scores = {}
    pairs_to_check = [(cand, node_in_a) for cand in candidates for node_in_a in group_A]
    for u, v, score in tqdm(nx.adamic_adar_index(B, pairs_to_check), total=len(pairs_to_check), desc="Adamic/Adar"):
        aa_scores[u] = aa_scores.get(u, 0) + score
    
    for cand in aa_scores:
        aa_scores[cand] /= len(group_A)

    print("3/4: Score normalization is in progress...")
    final_scores = {}
    
    candidate_rwr = {node: score for node, score in rwr_scores.items() if node in candidates}
    if not candidate_rwr or not aa_scores:
        print("Failed to calculate scores or no valid candidates.")
        return []

    min_rwr, max_rwr = min(candidate_rwr.values()), max(candidate_rwr.values())
    min_aa, max_aa = min(aa_scores.values()), max(aa_scores.values())

    for cand in candidates:
        score_rwr = candidate_rwr.get(cand, 0)
        norm_rwr = (score_rwr - min_rwr) / (max_rwr - min_rwr) if max_rwr > min_rwr else 0
        
        score_aa = aa_scores.get(cand, 0)
        norm_aa = (score_aa - min_aa) / (max_aa - min_aa) if max_aa > min_aa else 0
        
        final_scores[cand] = (alpha * norm_aa) + ((1 - alpha) * norm_rwr)

    print("4/4: Final ranking is being determined...")
    sorted_recommendations = sorted(final_scores.items(), key=lambda item: item[1], reverse=True)
    
    return sorted_recommendations[:top_n]

# --- function for outside ---
def get_recommendation_list(target_group_name, topn):
    # --- Load graph and data ---
    print(f"Load graph from '{GRAPH_FILE_PATH}'...")
    try:
        B = nx.read_graphml(GRAPH_FILE_PATH)
        all_image_nodes = {n for n, d in B.nodes(data=True) if d['bipartite'] == 0}
    except FileNotFoundError:
        print(f"Error: '{GRAPH_FILE_PATH}' file not found.")
        print("Please run create_graph.py first to generate the file.")
        return []

    # --- Load group info from JSON ---
    print(f"Load group info from '{JSON_FILE_PATH}'...")
    try:
        with open(JSON_FILE_PATH, 'r') as f:
            train_data = json.load(f)

        # Group images by their tags
        image_groups = {}
        for fname, tags in train_data.items():  # New JSON structure: filename -> [tags]
            for group_name in tags:
                if group_name not in image_groups:
                    image_groups[group_name] = []
                image_groups[group_name].append(fname)
        
    except FileNotFoundError:
        print(f"Error: '{JSON_FILE_PATH}' file not found.")
        return []

    # --- 3. Run recommendation system ---
    target_images = image_groups.get(target_group_name)

    if not target_images:
        print(f"Error: '{target_group_name}' group not found in JSON.")
        return []

    # Filter images that actually exist in the graph
    group_A = [img for img in target_images if img in all_image_nodes]
    
    if not group_A:
        print(f"Error: '{target_group_name}' group images do not exist in the graph.")
        return []

    print("\n==============================================")
    print(f"Recommendation base group: '{target_group_name}'")
    print(f"Images belonging to '{target_group_name}' group (#{len(group_A)} existing in graph): {group_A}")

    # Call recommendation function
    recommendations = recommend_images(B, group_A, all_image_nodes, top_n=topn, alpha=0.6)

    # Return only the recommendation list
    return [image for image, score in recommendations]

# --- Test when run independently ---
if __name__ == "__main__":
    get_recommendation_list(TARGET_GROUP_NAME, topn=10)
