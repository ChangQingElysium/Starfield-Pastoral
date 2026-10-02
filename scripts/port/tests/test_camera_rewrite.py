"""The camera conversion is retained for future main syncs and is idempotent."""
import importlib.util
from pathlib import Path
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "rewrite_1201.py"
spec = importlib.util.spec_from_file_location("port_rewrite", SCRIPT)
rewrite = importlib.util.module_from_spec(spec)
spec.loader.exec_module(rewrite)


class CameraRewriteTest(unittest.TestCase):
    def source(self, expression):
        return "package example;\nclass Bubble { void draw() { pose.mulPose(" + expression + "); } }\n"

    def test_dispatcher_getters(self):
        for receiver in ("Minecraft.getInstance().getEntityRenderDispatcher()", "mc.getEntityRenderDispatcher()",
                         "minecraft.getEntityRenderDispatcher()", "entityRenderDispatcher", "this.entityRenderDispatcher",
                         "dispatcher"):
            with self.subTest(receiver=receiver):
                converted = rewrite.rewrite_camera_api(self.source(receiver + ".cameraOrientation()"))
                self.assertIn("pose.mulPose(PortCamera.cameraOrientation(" + receiver + "))", converted)
                self.assertIn("import com.stardew.craft.port.PortCamera;", converted)
                self.assertEqual(rewrite.rewrite_camera_api(converted), converted)

    def test_event_camera(self):
        converted = rewrite.rewrite_camera_api(self.source("event.getCamera().rotation()"))
        self.assertIn("pose.mulPose(PortCamera.rotation(event.getCamera()))", converted)
        self.assertEqual(rewrite.rewrite_camera_api(converted), converted)

    def test_unrelated_rotation_untouched(self):
        original = self.source("quaternion.rotation()")
        self.assertEqual(rewrite.rewrite_camera_api(original), original)

    def test_existing_port_call_not_nested(self):
        original = self.source("PortCamera.cameraOrientation(dispatcher)")
        converted = rewrite.rewrite_camera_api(original)
        self.assertIn("pose.mulPose(PortCamera.cameraOrientation(dispatcher))", converted)
        self.assertNotIn("PortCamera.cameraOrientation(PortCamera", converted)

    def test_sync_pipeline_includes_conversion(self):
        converted = rewrite.rewrite(self.source("dispatcher.cameraOrientation()"), [])
        self.assertIn("PortCamera.cameraOrientation(dispatcher)", converted)


if __name__ == "__main__":
    unittest.main()
