import type { Response } from 'express';

const clients = new Set<Response>();
const audiences = new Map<Response,string>();
export function subscribe(response: Response, userId?:string) {
  clients.add(response);
  if(userId)audiences.set(response,userId);
  const heartbeat=setInterval(()=>response.write(`event: heartbeat\ndata: ${JSON.stringify({at:new Date().toISOString()})}\n\n`),10000);
  response.on('close', () => {clearInterval(heartbeat);clients.delete(response);audiences.delete(response);});
}
export function broadcastPrivate(userIds:string[],resource:string){
 const event=`event: update\ndata: ${JSON.stringify({resource,at:new Date().toISOString()})}\n\n`;
 for(const client of clients)if(userIds.includes(audiences.get(client)||''))client.write(event);
}
export function broadcast(resource: string, action: string) {
  const event = `event: update\ndata: ${JSON.stringify({ resource, action, at: new Date().toISOString() })}\n\n`;
  for (const client of clients) client.write(event);
}
