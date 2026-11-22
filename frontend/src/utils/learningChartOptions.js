const colors = ['#646cff', '#35c9ab', '#62b9ef', '#f6b965', '#db83b4']
const textStyle = { fontFamily: 'Microsoft YaHei, sans-serif', color: '#8491a5', fontSize: 11 }

export function learningChartOption(kind, data = []) {
  if (!data.length) return { title: { text: '暂无答题数据', left: 'center', top: 'center', textStyle: { color: '#8491a5', fontSize: 14, fontWeight: 400 } } }

  const shared = { color: colors, textStyle, animationDuration: 350 }
  const categoryAxis = {
    type: 'category', boundaryGap: false, data: data.map(d => d.day),
    axisLine: { lineStyle: { color: '#edf0f6' } }, axisTick: { show: false },
    axisLabel: { color: '#8491a5', formatter: value => value.slice(5) }
  }
  const valueAxis = { type: 'value', minInterval: 1, axisLabel: { color: '#8491a5' }, splitLine: { lineStyle: { color: '#f0f3f8', type: 'dashed' } } }

  if (kind === 'activity') return {
    ...shared,
    tooltip: { trigger: 'axis' },
    legend: { data: ['答题数', '正确数'], top: 0, right: 8, icon: 'circle', itemWidth: 7, itemHeight: 7, textStyle },
    grid: { left: 12, right: 16, top: 48, bottom: 8, containLabel: true },
    xAxis: categoryAxis, yAxis: valueAxis,
    series: [
      { name: '答题数', type: 'line', smooth: true, symbol: 'circle', symbolSize: 6, data: data.map(d => d.answered), lineStyle: { width: 2.5 }, areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: '#646cff40' }, { offset: 1, color: '#646cff03' }] } } },
      { name: '正确数', type: 'line', smooth: true, symbol: 'circle', symbolSize: 6, data: data.map(d => d.correct), lineStyle: { width: 2.5 }, areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: '#35c9ab38' }, { offset: 1, color: '#35c9ab03' }] } } }
    ]
  }

  if (kind === 'subject') return {
    ...shared,
    tooltip: { trigger: 'item', formatter: '{b}: {c} 题（{d}%）' },
    legend: { bottom: 0, icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle },
    series: [{ type: 'pie', radius: ['48%', '72%'], center: ['50%', '45%'], label: { show: false }, itemStyle: { borderColor: '#fff', borderWidth: 4, borderRadius: 7 }, emphasis: { label: { show: true, position: 'center', formatter: '{b}\n{c} 题', fontSize: 17, lineHeight: 28, fontWeight: 600, color: '#273044' } }, data: data.map(d => ({ name: d.subject, value: d.answered || 0 })) }]
  }

  if (kind === 'accuracy') return {
    ...shared,
    tooltip: { trigger: 'axis', formatter: '{b}<br/>{c}%' },
    grid: { left: 12, right: 16, top: 24, bottom: 8, containLabel: true },
    xAxis: { ...categoryAxis, boundaryGap: true },
    yAxis: { ...valueAxis, min: 0, max: 100, axisLabel: { color: '#8491a5', formatter: '{value}%' } },
    series: [{ type: 'bar', name: '正确率', barMaxWidth: 28, data: data.map(d => d.answered > 0 ? Math.round((d.correct || 0) * 100 / d.answered) : 0), itemStyle: { borderRadius: [5, 5, 0, 0], color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: '#969cff' }, { offset: 1, color: '#646cff' }] } } }]
  }

  // 少于三个科目时，横向条形图比单轴/双轴雷达更易读。
  if (data.length < 3) return {
    ...shared,
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 12, right: 48, top: 32, bottom: 12, containLabel: true },
    xAxis: { ...valueAxis, min: 0, max: 100, axisLabel: { color: '#8491a5', formatter: '{value}%' } },
    yAxis: { type: 'category', data: data.map(d => d.subject), axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: '#596579' } },
    series: [{ name: '正确率', type: 'bar', barMaxWidth: 22, showBackground: true, backgroundStyle: { color: '#f2f3ff', borderRadius: 5 }, label: { show: true, position: 'right', formatter: '{c}%', color: '#646cff' }, data: data.map(d => Math.round((d.accuracy || 0) * 100)), itemStyle: { color: '#868dff', borderRadius: 5 } }]
  }

  return {
    ...shared,
    tooltip: { trigger: 'item' },
    radar: { indicator: data.map(d => ({ name: d.subject, max: 100 })), center: ['50%', '53%'], radius: '68%', axisName: { color: '#78869b' }, splitArea: { show: false }, splitLine: { lineStyle: { color: '#e8ebfc' } }, axisLine: { lineStyle: { color: '#e8ebfc' } } },
    series: [{ type: 'radar', symbolSize: 5, data: [{ name: '答题正确率', value: data.map(d => Math.round((d.accuracy || 0) * 100)), areaStyle: { color: '#646cff2e' }, lineStyle: { width: 2 } }] }]
  }
}
