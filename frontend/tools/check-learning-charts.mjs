import assert from 'node:assert/strict'
import { learningChartOption } from '../src/utils/learningChartOptions.js'

for (const kind of ['activity', 'subject', 'accuracy', 'radar']) {
  assert.equal(learningChartOption(kind, []).title.text, '暂无答题数据')
}
const daily = [
  { day: '2026-11-20', answered: 10, correct: 7 },
  { day: '2026-11-21', answered: 0, correct: 0 }
]
const activity = learningChartOption('activity', daily)
assert.deepEqual(activity.series[0].data, [10, 0])
assert.deepEqual(activity.series[1].data, [7, 0])
assert.deepEqual(learningChartOption('accuracy', daily).series[0].data, [70, 0])
const subjects = [{ subject: '行测', answered: 10, accuracy: .7 }]
assert.equal(learningChartOption('subject', subjects).series[0].data[0].value, 10)
assert.equal(learningChartOption('radar', subjects).series[0].type, 'bar')
assert.deepEqual(learningChartOption('radar', subjects).series[0].data, [70])
assert.equal(learningChartOption('radar', [...subjects, ...subjects]).series[0].type, 'bar')
assert.equal(learningChartOption('radar', [...subjects, ...subjects, ...subjects]).series[0].type, 'radar')
console.log('Learning chart data, empty states, percentages and subject layouts: PASS')
