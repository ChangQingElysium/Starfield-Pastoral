import com.stardew.craft.port.PortCamera;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Source-derived camera-basis checks; no Minecraft client or OpenGL context is started. */
public final class PortCameraTest {
    private static final float EPSILON = 0.00001F;

    public static void main(String[] args) {
        int cases = 0;
        for (float yaw : new float[]{0, 37, 90, -90, 180, -179, 359}) {
            for (float pitch : new float[]{0, 30, -30, 80, -80, 90, -90}) {
                float y = (float) Math.toRadians(yaw);
                float p = (float) Math.toRadians(pitch);
                // These are the exact Camera#setRotation formulas from each version.
                Quaternionf camera1201 = new Quaternionf().rotationYXZ(-y, p, 0);
                Quaternionf original = new Quaternionf(camera1201);
                Quaternionf expected1211 = new Quaternionf().rotationYXZ((float) Math.PI - y, -p, 0);
                Quaternionf converted = PortCamera.from1201(camera1201);
                for (Vector3f axis : new Vector3f[]{new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, 1)}) {
                    assertNear(converted.transform(new Vector3f(axis)), expected1211.transform(new Vector3f(axis)),
                            "camera basis yaw=" + yaw + " pitch=" + pitch);
                }
                if (converted == camera1201 || !camera1201.equals(original, 0)) {
                    throw new AssertionError("shared camera quaternion was reused or changed");
                }
                // The viewer lies along -Z in 1.20.1's camera basis. The migrated bubble's
                // unchanged +Z offsets must be successively closer to that viewer.
                Vector3f towardsViewer = camera1201.transform(new Vector3f(0, 0, -1));
                Vector3f icon = converted.transform(new Vector3f(0, 0, 0.001F));
                Vector3f count = converted.transform(new Vector3f(0, 0, 0.002F));
                if (!(icon.dot(towardsViewer) > 0 && count.dot(towardsViewer) > icon.dot(towardsViewer))) {
                    throw new AssertionError("ready bubble icon/count ended up behind their background");
                }
                cases++;
            }
        }
        System.out.println("PortCamera: " + cases + " yaw/pitch cases match 1.21.1; front layers and shared quaternion verified");
    }

    private static void assertNear(Vector3f actual, Vector3f expected, String label) {
        if (actual.distance(expected) > EPSILON) {
            throw new AssertionError(label + ": " + actual + " != " + expected);
        }
    }
}
