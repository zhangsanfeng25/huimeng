export type Appearance={theme:string;accent:string;wallpaper:string};
const KEY='huimeng_appearance_v1';
export const defaultAppearance:Appearance={theme:'light',accent:'violet',wallpaper:'none'};
export function hasSavedAppearance(){return Boolean(localStorage.getItem(KEY))}
export function readAppearance():Appearance{try{return{...defaultAppearance,...JSON.parse(localStorage.getItem(KEY)||'{}')}}catch{return defaultAppearance}}
export function applyAppearance(value:Appearance,persist=false){const root=document.documentElement;root.dataset.theme=value.theme;root.dataset.accent=value.accent;root.dataset.wallpaper=value.wallpaper;if(persist)localStorage.setItem(KEY,JSON.stringify(value))}
