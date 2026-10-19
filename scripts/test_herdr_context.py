import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("herdr_context", Path(__file__).parents[1] / "app/src/main/assets/herdr_context.py")
helper = importlib.util.module_from_spec(spec)
spec.loader.exec_module(helper)


class ContextSafetyTest(unittest.TestCase):
    def test_auto_focus_requires_this_clients_tty_and_no_other_client(self):
        own = [{"pid": 7, "tty": "/dev/pts/4"}]
        other = {"pid": 8, "tty": "/dev/pts/5"}
        self.assertTrue(helper.automatic_focus(own, own, "/dev/pts/4"))
        self.assertFalse(helper.automatic_focus(own, own, "/dev/pts/5"))
        self.assertFalse(helper.automatic_focus(own + [other], own + [other], "/dev/pts/4"))
        self.assertFalse(helper.automatic_focus(own, own + [other], "/dev/pts/4"))
        self.assertFalse(helper.automatic_focus([], [], "/dev/pts/4"))

    def test_explicit_session_selector_takes_precedence(self):
        self.assertEqual("mobile", helper.session_argument(["herdr", "--session", "mobile"], {"HERDR_SESSION":"desktop"}))
        self.assertEqual("mobile", helper.session_argument(["herdr", "session", "attach", "mobile"], {}))
        self.assertEqual("mobile", helper.session_argument(["herdr"], {"HERDR_SESSION":"mobile"}))


if __name__ == "__main__":
    unittest.main()
