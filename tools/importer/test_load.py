import unittest

from load import mysql_tsv, validate_objective


class LoadContractTest(unittest.TestCase):
    def test_mysql_tsv_escapes_record_delimiters(self):
        self.assertEqual(r'a\tb\nc\\d', mysql_tsv('a\tb\nc\\d'))
        self.assertEqual(r'\N', mysql_tsv(None))

    def test_objective_quality_gate_rejects_placeholder(self):
        row = {
            'question_stem': '题目正在全力以赴征集',
            'option_a': '缺失', 'option_b': '缺失',
            'option_c': '缺失', 'option_d': '缺失',
            'answer': 'A',
        }
        self.assertEqual('placeholder', validate_objective(row))

    def test_objective_quality_gate_accepts_multiselect(self):
        row = {
            'question_stem': '下列说法正确的有几项?',
            'option_a': '甲', 'option_b': '乙',
            'option_c': '丙', 'option_d': '丁',
            'answer': 'AC',
        }
        self.assertIsNone(validate_objective(row))


if __name__ == '__main__':
    unittest.main()
