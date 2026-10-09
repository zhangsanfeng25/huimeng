import type { Response } from 'express';

const clients = new Set<Response>();
export function subscribe(response: Response) {
  clients.add(response);
  const heartbeat=setInterval(()=>response.write(`event: heartbeat\ndata: ${JSON.stringify({at:new Date().toISOString()})}\n\n`),10000);
  response.on('close', () => {clearInterval(heartbeat);clients.delete(response);});
}
export function broadcast(resource: string, action: string) {
  const event = `event: update\ndata: ${JSON.stringify({ resource, action, at: new Date().toISOString() })}\n\n`;
  for (const client of clients) client.write(event);
}
