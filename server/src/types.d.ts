declare global {
  namespace Express {
    interface Request {
      actor?: { id: string; name: string; roles: string[]; accountRoles?: string[]; merchantId?: string; activeRole?: string };
    }
  }
}
export {};
