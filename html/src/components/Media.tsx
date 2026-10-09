import { useEffect, useState } from 'react';
import { ImageOff } from 'lucide-react';
import { Modal } from './Ui';
export function mediaUrl(value: unknown): string {
  if (typeof value !== 'string' || !value.trim()) return '';
  try {
    const url = new URL(value, location.origin);
    if (!['http:', 'https:'].includes(url.protocol)) return '';
    if (['10.0.2.2', '127.0.0.1', 'localhost'].includes(url.hostname) && url.pathname.startsWith('/public/')) return url.pathname;
    return url.href;
  } catch { return ''; }
}
export function MediaImage({ src, alt, className = '' }: { src: unknown; alt: string; className?: string }) {
  const url = mediaUrl(src), [failed, setFailed] = useState(false);
  useEffect(() => setFailed(false), [url]);
  return url && !failed ? <img className={className} src={url} alt={alt} onError={() => setFailed(true)} /> : <span className={`media-missing ${className}`} role="img" aria-label={`${alt}：${url ? '原图缺失或不可访问' : '尚未上传'}`}><ImageOff size={24}/><small>{url ? '原图缺失或不可访问' : '尚未上传图片'}</small></span>;
}
export function ImageGallery({ images, label = '图片' }: { images: unknown[]; label?: string }) {
  const urls = [...new Set(images.filter(value => typeof value === 'string' && value) as string[])];
  const [preview, setPreview] = useState<string | null>(null);
  return <><div className="media-gallery">{urls.map((src, index) => <button className="media-tile" key={src} onClick={() => setPreview(src)} aria-label={`预览${label} ${index + 1}`}><MediaImage src={src} alt={`${label} ${index + 1}`}/><span>{label} {index + 1}</span></button>)}</div>{!urls.length ? <p className="muted">尚未上传图片</p> : null}{preview ? <Modal title={`${label}预览`} onClose={() => setPreview(null)} onConfirm={() => setPreview(null)} confirmText="关闭" hideCancel><div className="media-preview"><MediaImage src={preview} alt={label}/><a href={mediaUrl(preview)} target="_blank" rel="noreferrer">打开原图</a></div></Modal> : null}</>;
}
