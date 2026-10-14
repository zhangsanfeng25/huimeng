import {db} from '../core/db.js';
export function merchantApplication(ownerId:string){
  return db.prepare(`SELECT id,shop_name shopName,description,contact_name contactName,contact_phone contactPhone,
    business_address businessAddress,approval_status status,business_status businessStatus,
    COALESCE(rejection_reason,'') rejectionReason,applied_at appliedAt,reviewed_at reviewedAt,updated_at updatedAt,version
    FROM merchants WHERE owner_user_id=?`).get(ownerId)||null;
}
