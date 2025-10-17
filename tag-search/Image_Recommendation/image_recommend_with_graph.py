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
    # --- 1. 그래프 및 데이터 로딩 ---
    print(f"'{GRAPH_FILE_PATH}'에서 그래프를 불러옵니다...")
    try:
        B = nx.read_graphml(GRAPH_FILE_PATH)
        all_image_nodes = {n for n, d in B.nodes(data=True) if d['bipartite'] == 0}
    except FileNotFoundError:
        print(f"오류: '{GRAPH_FILE_PATH}' 파일이 없습니다.")
        print("먼저 create_graph.py를 실행하여 파일을 생성해주세요.")
        return []
    print("그래프 로딩 완료.")

    # --- 2. JSON에서 그룹 정보 로딩 ---
    print(f"'{JSON_FILE_PATH}'에서 그룹 정보를 불러옵니다...")
    try:
        with open(JSON_FILE_PATH, 'r') as f:
            train_data = json.load(f)
        
        # 그룹별로 이미지 파일 목록을 정리
        image_groups = {}
        for fname, tags in train_data.items():  # 새로운 JSON 구조: filename -> [tags]
            for group_name in tags:
                if group_name not in image_groups:
                    image_groups[group_name] = []
                image_groups[group_name].append(fname)
        print("그룹 정보 로딩 완료.")
        
    except FileNotFoundError:
        print(f"오류: '{JSON_FILE_PATH}' 파일이 없습니다.")
        return []

    # --- 3. 추천 시스템 실행 ---
    target_images = image_groups.get(target_group_name)

    if not target_images:
        print(f"오류: JSON 파일에 '{target_group_name}' 그룹이 존재하지 않습니다.")
        return []
    
    # JSON에 있는 이미지 중 실제 그래프에 존재하는 노드만 필터링
    group_A = [img for img in target_images if img in all_image_nodes]
    
    if not group_A:
        print(f"오류: '{target_group_name}' 그룹의 이미지들이 그래프에 존재하지 않습니다.")
        return []

    print("\n==============================================")
    print(f"추천 기준 그룹: '{target_group_name}'")
    print(f"'{target_group_name}' 그룹에 속한 이미지 (그래프에 존재하는 {len(group_A)}개): {group_A}")
    
    # 추천 함수 호출
    recommendations = recommend_images(B, group_A, all_image_nodes, top_n=topn, alpha=0.6)
    
    print("\n--- 최종 추천 결과 ---")
    if recommendations:
        for i, (image, score) in enumerate(recommendations):
            print(f"{i+1}위: {image} (점수: {score:.4f})")
    else:
        print("추천 결과를 생성하지 못했습니다.")
    print("==============================================")

    # 추천 리스트만 반환
    return [image for image, score in recommendations]

# --- 독립 실행 시 테스트 ---
if __name__ == "__main__":
    get_recommendation_list(TARGET_GROUP_NAME, topn=10)
