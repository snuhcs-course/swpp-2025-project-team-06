import os
import shutil
import sys
from image_recommend_with_graph import get_recommendation_list as get_graph_recommendation
from image_recommend_with_rep_vec import get_recommendation_list as get_rep_vec_recommendation

IMAGE_BASE_PATH = "/home/team6/real_image_grouped"
RESULT_BASE_PATH = "/home/team6/tag-search/PoC/results/image_recommendation"

def copy_images_to_folder(image_list, dest_folder):
    if os.path.exists(dest_folder):
        shutil.rmtree(dest_folder)
    os.makedirs(dest_folder)
    
    for i, img in enumerate(image_list, start=1):
        src_path = os.path.join(IMAGE_BASE_PATH, img)
        if not os.path.exists(src_path):
            print(f"Cannot find '{img}'. Skip.")
            continue
        dest_filename = f"{i:03d}-{img}"  # to keep order
        dest_path = os.path.join(dest_folder, dest_filename)
        shutil.copy2(src_path, dest_path)

def main():
    if len(sys.argv) < 3:
        print("Usage: uv run image_recommend.py <tag_name> <topk>")
        print("Tag example: [\"singing_room\", \"board\", \"person_in_room_escape\"]") # just for PoC
        sys.exit(1)

    target_group_name = sys.argv[1]
    try:
        topk = int(sys.argv[2])
    except ValueError:
        print("top-k should be integer.")
        sys.exit(1)

    # result folder by target_group_name
    folder_path = os.path.join(RESULT_BASE_PATH, target_group_name)
    
    # graph recommendation
    print(f"At image_recommend_with_graph.py, get recommend list of '{target_group_name}'...")
    list_graph = get_graph_recommendation(target_group_name, topk)
    print(f"image_recommend_with_graph.py recommend done : {len(list_graph)} photos")

    # rep vec recommendation
    print(f"At image_recommend_with_rep_vec.py, get recommend list of '{target_group_name}'...")
    list_rep_vec = get_rep_vec_recommendation(target_group_name)
    print(f"image_recommend_with_rep_vec.py recommend done : {len(list_rep_vec)} photos")

    # merge two recommendations
    common_set = set(list_graph) & set(list_rep_vec)
    result = [img for img in list_graph if img in common_set] # graph recommend first
        
    only_graph = [img for img in list_graph if img not in common_set]
    only_rep_vec = [img for img in list_rep_vec if img not in common_set]

    while (only_graph or only_rep_vec) and (len(result) < topk):
        if only_graph:
            result.append(only_graph.pop(0))
        if only_rep_vec:
            result.append(only_rep_vec.pop(0))

    print("\n".join(result)) # debug

    # copy to folder
    copy_images_to_folder(result[:topk], folder_path)
    print(f"Recommend {len(result[:topk])} photos at '{folder_path}'")

if __name__ == "__main__":
    main()
