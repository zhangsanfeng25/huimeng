type Value = Record<string, unknown>;
export function bookContents(value: unknown): string[] {
  if (Array.isArray(value)) return value.filter((entry): entry is string => typeof entry === 'string');
  if (typeof value === 'string') { try { return bookContents(JSON.parse(value)); } catch { return []; } }
  return [];
}
const fields = [
  ['authorName', '文字作者', 100], ['illustratorName', '绘图作者', 100], ['publisher', '发行出版社', 160],
  ['publicationDate', '发售日期', 10], ['authorBio', '作者简介', 3000], ['illustratorBio', '绘图作者简介', 3000],
  ['editorialReview', '编辑推荐', 1500],
] as const;
export function BookInformation({ value }: { value: Value }) {
  const chapters = bookContents(value.tableOfContents);
  return <section><h3>图书资料</h3>{value.metadataSource === 'fictional_demo' ? <p className="muted">本项目虚构图书资料</p> : null}
    <dl className="detail-list">{fields.map(([key, label]) => <div key={key}><dt>{label}</dt><dd className="full-text">{String(value[key] || '暂未填写')}</dd></div>)}</dl>
    <h3>图书目录</h3>{chapters.length ? <ol>{chapters.map((chapter, index) => <li key={index}>{chapter}</li>)}</ol> : <p className="muted">暂未填写目录</p>}
  </section>;
}
export function BookInformationEditor({ value, onChange }: { value: Value; onChange: (key: string, value: unknown) => void }) {
  return <details className="book-information-editor"><summary>图书资料（选填）</summary>
    <div className="book-information-fields">{fields.map(([key, label, maximum]) => <label key={key}>{label}{key.endsWith('Bio') || key === 'editorialReview'
      ? <textarea aria-label={label} maxLength={maximum} value={String(value[key] || '')} onChange={event => onChange(key, event.target.value)} />
      : <input aria-label={label} type={key === 'publicationDate' ? 'date' : 'text'} maxLength={maximum} value={String(value[key] || '')} onChange={event => onChange(key, event.target.value)} />}</label>)}
    <label>图书目录（每行一项，最多 80 项）<textarea aria-label="图书目录（每行一项，最多 80 项）" value={bookContents(value.tableOfContents).join('\n')} onChange={event => onChange('tableOfContents', event.target.value.split('\n'))} onBlur={event => onChange('tableOfContents', event.target.value.split('\n').map(line => line.trim()).filter(Boolean))} /></label></div>
  </details>;
}
