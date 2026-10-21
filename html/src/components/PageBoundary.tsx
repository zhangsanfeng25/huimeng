import {Component,type ReactNode} from 'react';

/** A running service may replace hashed chunks while an older tab is still open. */
export class PageBoundary extends Component<{children:ReactNode},{error:Error|null}>{
 state:{error:Error|null}={error:null};
 static getDerivedStateFromError(error:Error){return {error};}
 render(){
  if(!this.state.error)return this.props.children;
  const changed=/dynamically imported module|Loading chunk|module script|Importing a module/i.test(this.state.error.message);
  return <main className="page-recovery" role="alert"><section className="panel"><h1>{changed?'页面版本已更新':'页面暂时无法加载'}</h1><p>{changed?'服务已更新，请重新加载当前页面后继续操作。':'请重新加载后重试；尚未提交的操作不会自动执行。'}</p><button className="primary" onClick={()=>window.location.reload()}>重新加载页面</button></section></main>;
 }
}
