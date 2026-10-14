export type Actor=NonNullable<Express.Request['actor']>;
export type SyncAction='address.save'|'address.delete'|'favorite.set'|'cart.set'|'cart.remove'|'profile.save'|'profile.avatar'|'post.create'|'comment.create'|'post.like'|'notice.read'|'notice.delete'|'merchant.apply'|'merchant.profile'|'product.save'|'product.submit'|'product.offline'|'checkout.create'|'checkout.pay'|'order.cancel'|'order.confirm'|'order.ship'|'order.advance'|'rating.create';
export type SyncEnvelope={protocolVersion:1;clientId:string;operationId:string;action:SyncAction;entityId?:string;baseVersion?:number;payload:Record<string,unknown>;payloadHash:string};
export type SyncResult={operationId:string;entityId?:string;version?:number;data:unknown;replayed:boolean};
export class SyncError extends Error {constructor(public status:number,public code:string,message:string,public detail?:unknown){super(message);}}
