"""Host tests for the packaged read-only helper; no real Herdr sessions queried."""
import importlib.util
import os
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

helper_path = Path(__file__).resolve().parents[2] / 'src/Hexgate.App/res/helpers/herdr_context.py'
sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location('hexgate_context_helper', helper_path)
helper = importlib.util.module_from_spec(spec)
spec.loader.exec_module(helper)


class ContextTests(unittest.TestCase):
    def setUp(self):
        self.environment = patch.dict(os.environ, {'SSH_CONNECTION': 'own-fixture'})
        self.environment.start()
        self.addCleanup(self.environment.stop)

    def client(self, pid=1, tty='/dev/pts/7', config='/tmp/own-fixture', connection='own-fixture'):
        return dict(pid=pid, tty=tty, config=config, start='123', connection=connection)

    def snapshot(self):
        return dict(result=dict(snapshot=dict(focused_pane_id='w1:p1', panes=[
            dict(pane_id='w1:p1', cwd='/tmp/own-fixture', title='Own pane')])))

    def test_automatic_grant_requires_stable_unique_tty(self):
        one = [self.client()]
        self.assertTrue(helper.automatic_focus(one, one, '/dev/pts/7'))
        self.assertFalse(helper.automatic_focus(one, one, '/dev/pts/8'))
        self.assertFalse(helper.automatic_focus(one, [self.client(pid=2)], '/dev/pts/7'))
        two = one + [self.client(pid=2, tty='/dev/pts/8')]
        self.assertFalse(helper.automatic_focus(two, two, '/dev/pts/7'))

    def test_query_uses_this_clients_config(self):
        with patch.object(helper, 'terminal_clients', return_value=[self.client()]), patch.object(helper, 'bounded_snapshot', return_value=self.snapshot()) as query:
            value = helper.query('fixture', '/dev/pts/7')
            self.assertTrue(value['automatic'])
            self.assertEqual(value['focused'], 'w1:p1')
            command, environment = query.call_args.args
            self.assertEqual(command[1:], ['--session', 'fixture', 'api', 'snapshot'])
            self.assertEqual(environment['XDG_CONFIG_HOME'], '/tmp/own-fixture')
            self.assertNotIn('HERDR_SOCKET_PATH', environment)

    def test_multiple_clients_keep_choices_without_global_focus(self):
        clients = [self.client(), self.client(pid=2, tty='/dev/pts/8')]
        with patch.object(helper, 'terminal_clients', return_value=clients), patch.object(helper, 'bounded_snapshot', return_value=self.snapshot()):
            value = helper.query('fixture', '/dev/pts/7')
            self.assertFalse(value['automatic'])
            self.assertIsNone(value['focused'])
            self.assertEqual(len(value['panes']), 1)

    def test_mismatched_configuration_never_queries(self):
        clients = [self.client(), self.client(pid=2, config='/tmp/other-fixture')]
        with patch.object(helper, 'terminal_clients', return_value=clients), patch.object(helper, 'bounded_snapshot') as query:
            with self.assertRaises(ValueError):
                helper.query('fixture', '/dev/pts/7')
            query.assert_not_called()

    def test_wrong_tty_never_queries(self):
        with patch.object(helper, 'terminal_clients', return_value=[self.client()]), patch.object(helper, 'bounded_snapshot') as query:
            with self.assertRaises(ValueError):
                helper.query('fixture', '/dev/pts/9')
            query.assert_not_called()

    def test_other_ssh_connection_never_queries(self):
        with patch.object(helper, 'terminal_clients', return_value=[self.client(connection='other-fixture')]), patch.object(helper, 'bounded_snapshot') as query:
            with self.assertRaises(ValueError):
                helper.query('fixture', '/dev/pts/7')
            query.assert_not_called()

    def test_real_bounded_subprocess(self):
        self.assertEqual(helper.bounded_snapshot([sys.executable, '-c', 'print("{}")'], os.environ.copy()), {})
        with self.assertRaises(ValueError):
            helper.bounded_snapshot([sys.executable, '-c', 'import os; os.write(1, b"x" * (2*1024*1024))'], os.environ.copy())

    def test_real_subprocess_timeout(self):
        with self.assertRaises(TimeoutError):
            helper.bounded_snapshot([sys.executable, '-c', 'import time; time.sleep(30)'], os.environ.copy())


if __name__ == '__main__':
    unittest.main()
