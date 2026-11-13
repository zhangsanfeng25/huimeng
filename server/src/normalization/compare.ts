import type {BusinessBaseline,ComparisonReport} from './types.js';
export function compareBusinessBaselines(before:BusinessBaseline,after:BusinessBaseline):ComparisonReport{
 const result:ComparisonReport={equal:true,differences:[]};
 const add=(resource:string,kind:string,count:number)=>{result.equal=false;result.differences.push({resource,kind,count});};
 if(before.modelVersion!==after.modelVersion)add('baseline','model-version',1);
 for(const resource of new Set([...Object.keys(before.resources),...Object.keys(after.resources)])){
  const a=before.resources[resource],b=after.resources[resource];
  if(!a||!b){add(resource,'missing-relation',1);continue;}
  if(a.count!==b.count)add(resource,'row-count',Math.abs(a.count-b.count));
  if(a.digest!==b.digest){
   const counts=new Map<string,number>();for(const row of a.rows)counts.set(row,(counts.get(row)||0)+1);
   for(const row of b.rows)counts.set(row,(counts.get(row)||0)-1);
   add(resource,'row-content',Array.from(counts.values()).reduce((n,v)=>n+Math.abs(v),0));
  }
 }
 for(const key of new Set([...Object.keys(before.amounts),...Object.keys(after.amounts)]))if(before.amounts[key]!==after.amounts[key])add(key,'amount',1);
 for(const key of new Set([...Object.keys(before.counters),...Object.keys(after.counters)]))if(before.counters[key]!==after.counters[key])add(key,'autoincrement',1);
 return result;
}
