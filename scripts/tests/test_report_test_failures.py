"""Keep optional-fixture assumptions distinct from genuine test failures in CI diagnostics."""
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

REPORTER = Path(__file__).resolve().parents[1] / 'report-test-failures.py'


class ReporterTest(unittest.TestCase):
    def report(self, failure):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / 'app/build/outputs/androidTest-results/TEST-fixture.xml'
            output.parent.mkdir(parents=True)
            output.write_text('<testsuite tests="1"><testcase classname="Fixture" name="test">'
                              + failure + '</testcase></testsuite>')
            return subprocess.check_output([sys.executable, str(REPORTER)], cwd=directory, text=True)

    def test_untyped_connected_assumption_is_a_skip(self):
        output = self.report('<failure>\norg.junit.AssumptionViolatedException: missing fixture\n'
                             '\tat org.junit.Assume.assumeTrue(Assume.java:68)</failure>')
        self.assertNotIn('::error::', output)
        self.assertIn('0 failures/errors, 1 assumption skips', output)

    def test_typed_assumption_is_a_skip(self):
        output = self.report('<failure type="org.junit.internal.AssumptionViolatedException" '
                             'message="optional sample is absent"/>')
        self.assertNotIn('::error::', output)
        self.assertIn('0 failures/errors, 1 assumption skips', output)

    def test_assertion_mentioning_assumptions_is_not_hidden(self):
        output = self.report('<failure type="java.lang.AssertionError">'
                             'Expected org.junit.AssumptionViolatedException but saw a bug</failure>')
        self.assertIn('::error::Fixture.test:', output)
        self.assertIn('1 failures/errors, 0 assumption skips', output)


if __name__ == '__main__':
    unittest.main()
