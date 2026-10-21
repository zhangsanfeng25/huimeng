import { ReactNode, useEffect, useId, useRef } from 'react';
import { CheckCircle2, X } from 'lucide-react';
export function PageHeader({ title, description, action, onAction }: {
    title: string;
    description: string;
    action?: string;
    onAction?: () => void;
}) {
    return <div className="page-header"><div><h1>{title}</h1><p>{description}</p></div>{action ? <button className="primary" onClick={onAction}>{action}</button> : null}</div>;
}
export function StatusTag({ value }: {
    value: string | number;
}) {
    const saleLabels:Record<string,string>={MERCHANT_REVIEW:'待商家初审',ADMIN_REVIEW:'待平台审核',WAITING_RETURN:'待买家退货',RETURNING:'退货运输中',REFUND_PENDING:'待确认退款',COMPLETED:'售后完成',REJECTED:'已驳回',CANCELLED:'已撤销'};
    const text = saleLabels[String(value)]||String(value);
    const tone = /正常|通过|启用|公开|成功|完成|营业|销售|发送/.test(text) ? 'success' : /停用|隐藏|驳回|异常/.test(text) ? 'danger' : /待|未/.test(text) ? 'warning' : 'neutral';
    return <span className={`status ${tone}`}><i />{text}</span>;
}
export function Toast({ message, onClose }: {
    message: string;
    onClose: () => void;
}) {
    useEffect(() => { const id = window.setTimeout(onClose, 2600); return () => window.clearTimeout(id); }, [onClose]);
    return <div className="toast"><CheckCircle2 size={18}/><span>{message}</span><button onClick={onClose}><X size={15}/></button></div>;
}
export function Modal({ title, children, onClose, onConfirm, confirmText = '确认', hideCancel = false, danger = false, busy = false }: {
    title: string;
    children: ReactNode;
    onClose: () => void;
    onConfirm: () => void;
    confirmText?: string;
    hideCancel?: boolean;
    danger?: boolean;
    busy?: boolean;
}) {
    const titleId = useId(), element = useRef<HTMLElement>(null), closeRef = useRef(onClose);
    closeRef.current = onClose;
    const busyRef = useRef(busy);
    busyRef.current = busy;
    useEffect(() => { const previous = document.activeElement as HTMLElement | null; const dialog = element.current; dialog?.querySelector<HTMLElement>('button,input,select,textarea')?.focus(); const keyboard = (event: KeyboardEvent) => { const dialogs = document.querySelectorAll('[role="dialog"]'); if (dialogs[dialogs.length - 1] !== dialog)
        return; if (event.key === 'Escape' && !busyRef.current) {
        event.preventDefault();
        closeRef.current();
    } if (event.key === 'Tab') {
        const controls = Array.from(dialog?.querySelectorAll<HTMLElement>('button:not(:disabled),input:not(:disabled),textarea:not(:disabled),select:not(:disabled),a[href]') || []);
        const first = controls[0], last = controls[controls.length - 1];
        if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last?.focus();
        }
        else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first?.focus();
        }
    } }; document.addEventListener('keydown', keyboard); return () => { document.removeEventListener('keydown', keyboard); if (previous?.isConnected)
        previous.focus(); }; }, []);
    return <div className="modal-backdrop" role="presentation" onMouseDown={event => event.currentTarget === event.target && !busy && onClose()}><section ref={element} className="modal" role="dialog" aria-modal="true" aria-labelledby={titleId}><header><h2 id={titleId}>{title}</h2><button aria-label="关闭弹窗" disabled={busy} onClick={onClose}><X /></button></header><div className="modal-body">{children}</div><footer>{!hideCancel ? <button className="secondary" disabled={busy} onClick={onClose}>取消</button> : null}<button className={danger ? 'danger-button' : 'primary'} disabled={busy} onClick={onConfirm}>{busy ? '处理中…' : confirmText}</button></footer></section></div>;
}
