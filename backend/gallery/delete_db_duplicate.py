import sys
from django.db import transaction
from django.db.models import Count
from gallery.models import Photo
from gallery.qdrant_utils import get_qdrant_client, IMAGE_COLLECTION_NAME

client = get_qdrant_client()

print("--- [1부] SQL DB 중복 제거 작업을 시작합니다 ---")
try:
    with transaction.atomic():
        duplicates_query = (
            Photo.objects.values('user_id', 'photo_path_id')
            .annotate(count=Count('photo_id'))
            .filter(count__gt=1)
        )
        duplicate_groups = list(duplicates_query)

        if not duplicate_groups:
            print("✅ [SQL] 중복된 (user, photo_path_id) 조합이 없습니다.")
        else:
            print(f"🚨 [SQL] 총 {len(duplicate_groups)}개의 중복 그룹을 찾았습니다.")
            total_sql_deleted = 0
            
            for dup_group in duplicate_groups:
                user_id = dup_group['user_id']
                photo_path_id = dup_group['photo_path_id']
                
                all_duplicates_qs = Photo.objects.filter(
                    user_id=user_id,
                    photo_path_id=photo_path_id
                )
                photo_to_keep = all_duplicates_qs.order_by('-created_at').first()

                if photo_to_keep is None: 
                    print(f"  - 경고: User {user_id}, Path {photo_path_id} 그룹에서 유지할 사진을 찾지 못해 건너뜁니다.")
                    continue

                photos_to_delete_qs = all_duplicates_qs.exclude(photo_id=photo_to_keep.photo_id)
                deleted_count, _ = photos_to_delete_qs.delete()
                
                print(f"  - User {user_id}, Path {photo_path_id}: {deleted_count}개 삭제됨 (유지: {photo_to_keep.photo_id}).")
                total_sql_deleted += deleted_count
            
            print(f"✅ [SQL] 총 {total_sql_deleted}개의 중복 Photo 행을 삭제했습니다.")
    print("--- [1부] SQL DB 작업 완료 (커밋됨) ---")
except Exception as e:
    print(f"🚨🚨🚨 [1부 실패!] SQL 트랜잭션이 롤백되었습니다! 🚨🚨🚨")
    print(f"에러: {e}")
    print("DB 변경사항이 모두 취소되었습니다. Qdrant 작업을 시작하지 않습니다.")
    sys.exit(1)

print("\n--- [2부] Qdrant 'IMAGE_COLLECTION_NAME' 고아 벡터 제거 작업을 시작합니다 ---")
try:
    print("[SQL] 모든 유효한 사진 ID를 로드 중...")
    sql_ids_qs = Photo.objects.all().values_list('photo_id', flat=True)
    sql_ids_str = {str(pid) for pid in sql_ids_qs}
    print(f"[SQL] 총 {len(sql_ids_str)}개의 유효한 사진 ID를 확인했습니다.")

    print(f"[QDRANT] '{IMAGE_COLLECTION_NAME}' 스캔 중... (시간이 걸릴 수 있습니다)")
    qdrant_ids = set()
    next_offset = 0
    while True:
        points, next_offset_val = client.scroll(
            collection_name=IMAGE_COLLECTION_NAME,
            limit=1000, 
            offset=next_offset, 
            with_payload=False, 
            with_vectors=False
        )
        
        if points:
            for point in points: 
                qdrant_ids.add(point.id)
        
        if next_offset_val is None:
            break
        
        next_offset = next_offset_val

    print(f"[QDRANT] 총 {len(qdrant_ids)}개의 벡터 ID를 확인했습니다.")

    zombie_ids_to_delete = list(qdrant_ids - sql_ids_str)

    if not zombie_ids_to_delete:
        print(f"✅ [QDRANT] '{IMAGE_COLLECTION_NAME}'에 고아 벡터가 없습니다.")
    else:
        print(f"🚨 [QDRANT] {len(zombie_ids_to_delete)}개의 고아 벡터를 찾아 삭제합니다.")
        
        client.delete(
            collection_name=IMAGE_COLLECTION_NAME,
            points_selector=zombie_ids_to_delete,
            wait=True
        )
        print(f"✅ [QDRANT] '{IMAGE_COLLECTION_NAME}' 삭제 완료.")
    print("--- [2부] Qdrant 이미지 작업 완료 ---")
except Exception as e:
    print(f"🚨🚨🚨 [2부 실패!] Qdrant 작업 중 오류 발생! 🚨🚨🚨")
    print(f"에러: {e}")

print("\n🎉🎉🎉 SQL DB 중복 제거 및 이미지 벡터 정리 작업이 완료되었습니다. 🎉🎉🎉")