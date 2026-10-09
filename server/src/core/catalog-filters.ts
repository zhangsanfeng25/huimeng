/** Existing free-form ages remain supported without rewriting product data. */
export function matchesAge(value: string, selection: string): number {
  if (!selection) return 1;
  const selected = selection.match(/^(\d+)-(\d+|plus)$/);
  if (!selected) return 0;
  const lower = Number(selected[1]), upper = selected[2] === 'plus' ? Infinity : Number(selected[2]);
  const normalized = value.replace(/[–—～~至]/g, '-');
  const range = normalized.match(/(\d+)\s*-\s*(\d+)/);
  if (range) return Number(range[1]) <= upper && Number(range[2]) >= lower ? 1 : 0;
  const single = normalized.match(/(\d+)\s*岁/);
  if (!single) return 0;
  const age = Number(single[1]);
  return /以上|\+/.test(normalized) ? (age <= upper ? 1 : 0) : (age >= lower && age <= upper ? 1 : 0);
}

export const themeTerms: Record<string, string[]> = {
  bedtime: ['睡前', '晚安', '安睡'],
  emotion: ['情绪', '朋友', '勇敢', '成长', '上学', '分享'],
  science: ['科普', '百科', '海洋', '昆虫', '恐龙', '地球', '星空', '四季']
};
