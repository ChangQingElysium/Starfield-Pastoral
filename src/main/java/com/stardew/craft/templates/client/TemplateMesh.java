package com.stardew.craft.templates.client;

import com.stardew.craft.templates.MaterialTemplateBlock;
import com.stardew.craft.templates.HalfStairsTemplateBlock;
import com.stardew.craft.templates.RoofTemplateForm;
import com.stardew.craft.templates.SmartRidgeTemplateBlock;
import com.stardew.craft.templates.SmartRoofTemplateBlock;
import com.stardew.craft.templates.SnowLayerTemplateBlock;
import com.stardew.craft.templates.TemplateBox;
import com.stardew.craft.templates.TemplateShape;
import com.stardew.craft.templates.TemplateShapeCache;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.StairsShape;
import org.joml.Vector3f;

final class TemplateMesh {
    private final List<MeshQuad> quads;

    private TemplateMesh(List<MeshQuad> quads) {
        this.quads = List.copyOf(quads);
    }

    static TemplateMesh create(TemplateShape shape, BlockState state, boolean itemModel) {
        return create(shape, state, itemModel, 0, 0);
    }

    static TemplateMesh create(TemplateShape shape, BlockState state, boolean itemModel, int phaseX, int phaseZ) {
        return create(shape, state, itemModel, phaseX, phaseZ, 15, false);
    }

    static TemplateMesh createFill(TemplateShape shape, BlockState state) {
        if (shape == TemplateShape.ROUND_WINDOW) return roundWindowLayer(state,
                com.stardew.craft.templates.RoundWindowProfile.fill(), List.of(), false);
        return create(shape, state, false, 0, 0, 0, true);
    }

    static TemplateMesh roundWindowLayer(BlockState state, List<TemplateBox> boxes,
                                          List<TemplateBox> hiddenBy, boolean glass) {
        var faces = new ArrayList<MeshQuad>();
        addBoxUnion(faces, boxes);
        // The masks meet along whole faces of this authored profile. Keep only
        // one interface for translucent pairs; never offset the geometry.
        faces.removeIf(q -> {
            if (glass && q.direction() != Direction.NORTH && q.direction() != Direction.SOUTH) return true;
            if (hiddenBy.isEmpty()) return false;
            Vector3f center = new Vector3f();
            q.vertices().forEach(center::add);
            center.div(4);
            return q.vertices().stream().allMatch(v -> {
                Vector3f sample = new Vector3f(v).lerp(center, .001F).mul(16)
                        .add(new Vector3f(q.direction().step()).mul(.001F));
                return hiddenBy.stream().anyMatch(b -> contains(b, sample.x, sample.y, sample.z));
            });
        });
        int turns = TemplateShapeCache.turnsFrom(Direction.NORTH, state.getValue(MaterialTemplateBlock.FACING));
        boolean flipped = state.getValue(MaterialTemplateBlock.FLIPPED);
        return new TemplateMesh(faces.stream().map(q -> q.transformed(turns * 90F, flipped)).toList());
    }

    static TemplateMesh create(TemplateShape shape, BlockState state, boolean itemModel, int phaseX, int phaseZ, int edges) {
        return create(shape, state, itemModel, phaseX, phaseZ, edges, false);
    }

    static TemplateMesh createRoof(TemplateShape shape, BlockState state, boolean itemModel,
                                   int phaseX, int phaseY, int phaseZ, int edges) {
        return create(shape, state, itemModel, phaseX, phaseZ, edges, false, phaseY);
    }

    TemplateMesh studyTexture(int phaseX, int phaseZ) {
        return new TemplateMesh(RoofTemplateDetails.studyTexture(quads, phaseX, phaseZ));
    }

    static TemplateMesh windowArtwork(TemplateShape shape, BlockState state) {
        int mask = com.stardew.craft.templates.ConnectedFacadeTemplateBlock.connections(state);
        var parts = new ArrayList<>(com.stardew.craft.templates.WindowFrameProfile.parts(mask));
        if (shape == TemplateShape.WINDOW_TRANSOM) parts.add(new com.stardew.craft.templates.WindowFrameProfile.Part(
                new TemplateBox((mask&8)==0?2:1,7,3,(mask&2)==0?14:15,9,6), "divider", true));
        var result = new ArrayList<MeshQuad>();
        for (var part : parts) {
            var faces = new ArrayList<MeshQuad>();
            addBoxUnion(faces, List.of(part.box()));
            for (var face : faces) result.add(WindowFrameTexture.apply(face, part));
        }
        int turns = TemplateShapeCache.turnsFrom(shape.baseFacing(), state.getValue(MaterialTemplateBlock.FACING));
        boolean flipped = state.getValue(MaterialTemplateBlock.FLIPPED);
        return new TemplateMesh(result.stream().map(q -> q.transformed(turns*90F, flipped)).toList());
    }

    private static TemplateMesh create(TemplateShape shape, BlockState state, boolean itemModel,
                                        int phaseX, int phaseZ, int edges, boolean fillLayer) {
        return create(shape, state, itemModel, phaseX, phaseZ, edges, fillLayer, 0);
    }

    private static TemplateMesh create(TemplateShape shape, BlockState state, boolean itemModel,
                                       int phaseX, int phaseZ, int edges, boolean fillLayer, int phaseY) {
        List<MeshQuad> quads = new ArrayList<>();
        if (fillLayer && (shape.isCompositeWall() || shape.isWindow())) {
            addBoxUnion(quads, com.stardew.craft.templates.FacadeTemplateGeometry.fill(shape,
                    com.stardew.craft.templates.ConnectedFacadeTemplateBlock.connections(state)));
            if (shape.isWindow()) quads.removeIf(q -> q.direction()!=Direction.NORTH && q.direction()!=Direction.SOUTH);
        } else switch (shape.meshKind()) {
            case BOXES -> addBoxShape(quads, shape, state, itemModel);
            case SLOPE -> addWedge(quads, 0F, 1F, 0F, 1F, 0F, 1F, 0F);
            case SLOPE_EDGE -> addWedge(quads, 0F, 1F, 0F, 0.5F, 0F, 0.5F, 0F);
            case ELEVATED_SLOPE_EDGE -> addWedge(quads, 0F, 1F, 0F, 1F, 0F, 1F, 0.5F);
            case ROOF -> addRoof(quads, shape.roofForm(), state);
            case GABLE_PANEL -> addWedge(quads, 0F, 3F / 16F, 0F, 1F, 0F, 1F, 0F);
            case WALL_BRACE -> addWallBrace(quads, shape == TemplateShape.WALL_BRACE_RIGHT);
        }

        if (shape.meshKind() == TemplateShape.MeshKind.ROOF) {
            quads = roofLayer(quads, fillLayer);
        }
        Direction facing = state.hasProperty(MaterialTemplateBlock.FACING)
                ? state.getValue(MaterialTemplateBlock.FACING) : Direction.NORTH;
        boolean flipped = state.hasProperty(MaterialTemplateBlock.FLIPPED)
                && state.getValue(MaterialTemplateBlock.FLIPPED);
        int turns = shape == TemplateShape.ROOF_RIDGE ? 0 : TemplateShapeCache.turnsFrom(shape.baseFacing(), facing);
        float yaw = turns * 90F;
        List<MeshQuad> transformed = new ArrayList<>(quads.size());
        for (MeshQuad quad : quads) {
            transformed.add(quad.transformed(yaw, false));
        }
        if (shape.meshKind() == TemplateShape.MeshKind.ROOF && !fillLayer) {
            transformed = RoofTemplateDetails.add(transformed, shape, state, phaseX, phaseZ, edges, phaseY);
            List<MeshQuad> textured = new ArrayList<>();
            for (MeshQuad quad : transformed) tileRoofSurface(textured, quad, phaseX, phaseZ);
            return new TemplateMesh(flipped ? textured.stream().map(q -> q.transformed(0, true)).toList() : textured);
        }
        return new TemplateMesh(flipped ? transformed.stream().map(q -> q.transformed(0, true)).toList() : transformed);
    }

    /** The grid profile is the underside. Adding thickness above it preserves
     * both edge sections across rising cells and leaves the infill inside its cell. */
    private static List<MeshQuad> roofLayer(List<MeshQuad> envelope, boolean fill) {
        List<MeshQuad> out = new ArrayList<>();
        for (MeshQuad face : envelope) {
            if (face.direction() != Direction.UP) continue;
            Vector3f a = face.vertices().get(0), b = face.vertices().get(1), c = face.vertices().get(2);
            Vector3f n = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
            if (Math.abs(n.y) < 1E-7F) continue;
            Scalar underside = p -> a.y - (n.x * (p.x-a.x) + n.z * (p.z-a.z)) / n.y;
            List<PlanPoint> polygon = face.vertices().stream().map(v -> new PlanPoint(v.x, v.z)).distinct().toList();
            emitRoofLayer(out, polygon, fill ? underside : p -> underside.value(p) + RoofTemplateForm.SHELL_THICKNESS,
                    fill ? p -> 0F : underside, fill);
        }
        return out;
    }

    private static void emitRoofLayer(List<MeshQuad> out, List<PlanPoint> polygon, Scalar top, Scalar bottom, boolean fill) {
        if (polygon.size() < 3 || polygon.stream().noneMatch(p -> top.value(p)-bottom.value(p) > 1E-6F)) return;
        PlanPoint a = com.stardew.craft.port.PortJava.getFirst(polygon);
        for (int i = 1; i < polygon.size()-1; i++) {
            PlanPoint b = polygon.get(i), c = polygon.get(i+1);
            if (Math.abs((b.x-a.x)*(c.z-a.z)-(b.z-a.z)*(c.x-a.x)) < 1E-8F) continue;
            out.add(quad(Direction.UP, v(a.x,top.value(a),a.z), v(b.x,top.value(b),b.z),
                    v(c.x,top.value(c),c.z), v(c.x,top.value(c),c.z)).withPart(fill ? RoofPart.NONE : RoofPart.FIELD));
            out.add(quad(Direction.DOWN, v(c.x,bottom.value(c),c.z), v(b.x,bottom.value(b),b.z),
                    v(a.x,bottom.value(a),a.z), v(a.x,bottom.value(a),a.z)).withPart(fill ? RoofPart.NONE : RoofPart.UNDERSIDE));
        }
        for (int i = 0; i < polygon.size(); i++) {
            PlanPoint b = polygon.get(i), c = polygon.get((i+1)%polygon.size());
            Direction side = boundaryDirection(b,c);
            // Steep half-cell pieces terminate at an interior grid line.
            // Their old zero-thickness tip needed no cap; the solid section does.
            if (side == null && Math.abs(bottom.value(b)) < 1E-6F && Math.abs(bottom.value(c)) < 1E-6F
                    && (Math.abs(b.x-c.x) < 1E-6F || Math.abs(b.z-c.z) < 1E-6F))
                side = Direction.getNearest(b.z-c.z, 0, c.x-b.x);
            if (side != null && Math.max(top.value(b)-bottom.value(b),top.value(c)-bottom.value(c)) > 1E-6F) {
                out.add(quad(side, v(b.x,top.value(b),b.z), v(b.x,bottom.value(b),b.z),
                        v(c.x,bottom.value(c),c.z), v(c.x,top.value(c),c.z)).withPart(fill ? RoofPart.NONE : RoofPart.EDGE));
            }
        }
    }

    private static void tileRoofSurface(List<MeshQuad> out, MeshQuad quad, int phaseX, int phaseZ) {
        if (quad.studyPoints() != null) { out.add(quad); return; }
        Vector3f a = com.stardew.craft.port.PortJava.getFirst(quad.vertices());
        Vector3f normal = new Vector3f(quad.vertices().get(1)).sub(a)
                .cross(new Vector3f(quad.vertices().get(2)).sub(a));
        if (Math.abs(normal.y) < 1E-6F) { out.add(quad); return; }
        float gx = -normal.x / normal.y, gz = -normal.z / normal.y;
        if (Math.abs(gx) + Math.abs(gz) < 1E-6F) { out.add(quad); return; }
        boolean alongX = Math.abs(gx) > Math.abs(gz);
        float gradient = alongX ? gx : gz;
        // Whole pixel advance per world cell gives stable seams and a bounded
        // 16-phase cache, within 2% of native density for the supported pitches.
        float scale = Math.round(16F * (float) Math.sqrt(1F + gradient * gradient)) / 16F;
        float phase = ((alongX ? phaseX : phaseZ) * scale) % 1F;
        List<PlanPoint> polygon = quad.vertices().stream().map(v -> new PlanPoint(v.x, v.z)).distinct().toList();
        Scalar distance = p -> (alongX ? p.x : p.z) * scale + phase;
        for (int tile = 0; tile < Math.ceil(scale + phase); tile++) {
            int band = tile;
            List<PlanPoint> part = clip(polygon, p -> band - distance.value(p));
            part = clip(part, p -> distance.value(p) - band - 1F);
            if (part.size() < 3) continue;
            PlanPoint first = com.stardew.craft.port.PortJava.getFirst(part);
            for (int i = 1; i < part.size()-1; i++) {
                PlanPoint second = part.get(i), third = part.get(i+1);
                List<PlanPoint> points = List.of(first, second, third, third);
                List<Vector3f> vertices = points.stream().map(p -> v(p.x, a.y + gx*(p.x-a.x) + gz*(p.z-a.z), p.z)).toList();
                if (new Vector3f(vertices.get(1)).sub(com.stardew.craft.port.PortJava.getFirst(vertices)).cross(new Vector3f(vertices.get(2)).sub(com.stardew.craft.port.PortJava.getFirst(vertices))).lengthSquared() < 1E-10F) continue;
                out.add(new MeshQuad(quad.direction(), quad.textureDirection(), vertices,
                        points.stream().map(p -> uv(alongX ? p.z : p.x, Math.max(0,Math.min(1,distance.value(p)-band)))).toList(), quad.part(), null));
            }
        }
    }

    private static void addBoxShape(List<MeshQuad> out, TemplateShape shape, BlockState state, boolean itemModel) {
        if (shape == TemplateShape.GRID_WINDOW) {
            addBoxUnion(out, com.stardew.craft.templates.GridWindowProfile.frame(state.getValue(com.stardew.craft.templates.GridWindowTemplateBlock.CONNECTIONS)));
            return;
        }
        if (shape == TemplateShape.BALCONY_RAILING) {
            addBoxUnion(out, com.stardew.craft.templates.BalconyRailingProfile.boxes(state));
            return;
        }
        if (shape == TemplateShape.CHIMNEY) {
            addBoxUnion(out, com.stardew.craft.templates.ChimneyTemplateBlock.boxes(state));
            return;
        }
        int layers = !itemModel && state.hasProperty(SnowLayerTemplateBlock.LAYERS)
                ? state.getValue(SnowLayerTemplateBlock.LAYERS) : 1;
        addBoxUnion(out, shape.isWindow() || shape==TemplateShape.WALL_JUNCTION
                ? com.stardew.craft.templates.FacadeTemplateGeometry.frame(shape,com.stardew.craft.templates.ConnectedFacadeTemplateBlock.connections(state))
                : HalfStairsTemplateBlock.boxes(shape.collisionBoxes(StairsShape.STRAIGHT, layers),
                        state.hasProperty(HalfStairsTemplateBlock.MIRRORED) && state.getValue(HalfStairsTemplateBlock.MIRRORED)));
    }

    List<MeshQuad> quads() {
        return quads;
    }

    TemplateMesh withoutJoinedEdges(int hidden) {
        return new TemplateMesh(quads.stream().filter(q -> q.part()!=RoofPart.EDGE || q.cullFace()==null
                || q.cullFace().getAxis()==Direction.Axis.Y || (hidden & (1 << switch(q.cullFace()) {
                    case NORTH -> 0;case EAST -> 1;case SOUTH -> 2;default -> 3;
                }))==0).toList());
    }

    TemplateMesh withoutInterface(BlockState state, boolean fillLayer) {
        boolean flip = state.hasProperty(MaterialTemplateBlock.FLIPPED) && state.getValue(MaterialTemplateBlock.FLIPPED);
        Direction direction = fillLayer ? (flip ? Direction.DOWN : Direction.UP) : (flip ? Direction.UP : Direction.DOWN);
        return new TemplateMesh(quads.stream().filter(q -> q.direction() != direction
                || (!fillLayer && q.vertices().stream().allMatch(v -> Math.abs((flip ? 1-v.y : v.y)) < 1E-6F))).toList());
    }

    TemplateMesh withoutFacadeBack(BlockState state) {
        Direction back=state.getValue(MaterialTemplateBlock.FACING).getOpposite();
        return new TemplateMesh(quads.stream().filter(q -> q.direction()!=back).toList());
    }

    TemplateMesh withOpenFacadeFront(TemplateMesh frame, BlockState state) {
        Direction front=state.getValue(MaterialTemplateBlock.FACING);
        List<MeshQuad> out=new ArrayList<>();
        for(MeshQuad q:quads) {
            if(q.direction()!=front) {out.add(q);continue;}
            List<List<Vector3f>> pieces=new ArrayList<>();pieces.add(q.vertices().stream().distinct().toList());
            for(MeshQuad mask:frame.quads) if(mask.direction()==front) {
                var polygon=mask.vertices().stream().distinct().toList();
                List<List<Vector3f>> next=new ArrayList<>();
                for(var piece:pieces) {
                    var remainder=piece;
                    for(int i=0;i<polygon.size();i++) {
                        Vector3f a=polygon.get(i),b=polygon.get((i+1)%polygon.size());
                        Vector3f outward=new Vector3f(b).sub(a).cross(new Vector3f(front.step()));
                        if(outward.lengthSquared()<1E-10)continue;
                        java.util.function.ToDoubleFunction<Vector3f> boundary=v->new Vector3f(v).sub(a).dot(outward);
                        var outside=clipFacade(remainder,v->-boundary.applyAsDouble(v));
                        if(outside.size()>=3)next.add(outside);
                        remainder=clipFacade(remainder,boundary);
                    }
                }
                pieces=next;
            }
            for(var piece:pieces) for(int i=1;i<piece.size()-1;i++) {
                Vector3f a=com.stardew.craft.port.PortJava.getFirst(piece),b=piece.get(i),c=piece.get(i+1);
                if(new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a)).lengthSquared()>1E-10)
                    out.add(quad(front,a,b,c,c));
            }
        }
        return new TemplateMesh(out);
    }

    private static List<Vector3f> clipFacade(List<Vector3f> p, java.util.function.ToDoubleFunction<Vector3f> f) {
        if(p.isEmpty())return p;
        List<Vector3f> out=new ArrayList<>();Vector3f a=com.stardew.craft.port.PortJava.getLast(p);double fa=f.applyAsDouble(a);
        for(Vector3f b:p) {
            double fb=f.applyAsDouble(b);
            if((fa<=1E-6)!=(fb<=1E-6))out.add(new Vector3f(a).lerp(b,(float)Math.max(0,Math.min(1,fa/(fa-fb)))));
            if(fb<=1E-6)out.add(b);
            a=b;fa=fb;
        }
        return out;
    }

    int roofPhaseMask() {
        int mask = 0;
        for (MeshQuad q : quads) {
            Vector3f normal = new Vector3f(q.vertices().get(1)).sub(com.stardew.craft.port.PortJava.getFirst(q.vertices()))
                    .cross(new Vector3f(q.vertices().get(2)).sub(com.stardew.craft.port.PortJava.getFirst(q.vertices())));
            if (Math.abs(normal.y) < 1E-6F) continue;
            if (Math.abs(normal.x) > 1E-6F) mask |= 15;
            if (Math.abs(normal.z) > 1E-6F) mask |= 240;
        }
        return mask;
    }

    private static void addBoxUnion(List<MeshQuad> out, List<TemplateBox> boxes) {
        if (boxes.isEmpty()) return;
        List<Float> xs = coordinates(boxes, Axis.X);
        List<Float> ys = coordinates(boxes, Axis.Y);
        List<Float> zs = coordinates(boxes, Axis.Z);
        boolean[][][] filled = new boolean[xs.size() - 1][ys.size() - 1][zs.size() - 1];

        for (int x = 0; x < filled.length; x++) {
            for (int y = 0; y < filled[x].length; y++) {
                for (int z = 0; z < filled[x][y].length; z++) {
                    float midX = (xs.get(x) + xs.get(x + 1)) / 2F;
                    float midY = (ys.get(y) + ys.get(y + 1)) / 2F;
                    float midZ = (zs.get(z) + zs.get(z + 1)) / 2F;
                    filled[x][y][z] = boxes.stream().anyMatch(box -> contains(box, midX, midY, midZ));
                }
            }
        }

        int nx = filled.length;
        int ny = filled[0].length;
        int nz = filled[0][0].length;

        for (int z = 0; z < nz; z++) {
            int layer = z;
            int northPlane = z;
            int southPlane = z + 1;
            emitRectangles(faceMask(nx, ny, (x, y) -> filled[x][y][layer]
                            && (layer == 0 || !filled[x][y][layer - 1])),
                    (x0, y0, x1, y1) -> out.add(quad(Direction.NORTH,
                            v(at(xs, x1), at(ys, y0), at(zs, northPlane)), v(at(xs, x0), at(ys, y0), at(zs, northPlane)),
                            v(at(xs, x0), at(ys, y1), at(zs, northPlane)), v(at(xs, x1), at(ys, y1), at(zs, northPlane)))));
            emitRectangles(faceMask(nx, ny, (x, y) -> filled[x][y][layer]
                            && (layer == nz - 1 || !filled[x][y][layer + 1])),
                    (x0, y0, x1, y1) -> out.add(quad(Direction.SOUTH,
                            v(at(xs, x0), at(ys, y0), at(zs, southPlane)), v(at(xs, x1), at(ys, y0), at(zs, southPlane)),
                            v(at(xs, x1), at(ys, y1), at(zs, southPlane)), v(at(xs, x0), at(ys, y1), at(zs, southPlane)))));
        }

        for (int x = 0; x < nx; x++) {
            int layer = x;
            int westPlane = x;
            int eastPlane = x + 1;
            emitRectangles(faceMask(nz, ny, (z, y) -> filled[layer][y][z]
                            && (layer == 0 || !filled[layer - 1][y][z])),
                    (z0, y0, z1, y1) -> out.add(quad(Direction.WEST,
                            v(at(xs, westPlane), at(ys, y0), at(zs, z0)), v(at(xs, westPlane), at(ys, y0), at(zs, z1)),
                            v(at(xs, westPlane), at(ys, y1), at(zs, z1)), v(at(xs, westPlane), at(ys, y1), at(zs, z0)))));
            emitRectangles(faceMask(nz, ny, (z, y) -> filled[layer][y][z]
                            && (layer == nx - 1 || !filled[layer + 1][y][z])),
                    (z0, y0, z1, y1) -> out.add(quad(Direction.EAST,
                            v(at(xs, eastPlane), at(ys, y0), at(zs, z1)), v(at(xs, eastPlane), at(ys, y0), at(zs, z0)),
                            v(at(xs, eastPlane), at(ys, y1), at(zs, z0)), v(at(xs, eastPlane), at(ys, y1), at(zs, z1)))));
        }

        for (int y = 0; y < ny; y++) {
            int layer = y;
            int downPlane = y;
            int upPlane = y + 1;
            emitRectangles(faceMask(nx, nz, (x, z) -> filled[x][layer][z]
                            && (layer == 0 || !filled[x][layer - 1][z])),
                    (x0, z0, x1, z1) -> out.add(quad(Direction.DOWN,
                            v(at(xs, x0), at(ys, downPlane), at(zs, z1)), v(at(xs, x0), at(ys, downPlane), at(zs, z0)),
                            v(at(xs, x1), at(ys, downPlane), at(zs, z0)), v(at(xs, x1), at(ys, downPlane), at(zs, z1)))));
            emitRectangles(faceMask(nx, nz, (x, z) -> filled[x][layer][z]
                            && (layer == ny - 1 || !filled[x][layer + 1][z])),
                    (x0, z0, x1, z1) -> out.add(quad(Direction.UP,
                            v(at(xs, x0), at(ys, upPlane), at(zs, z0)), v(at(xs, x0), at(ys, upPlane), at(zs, z1)),
                            v(at(xs, x1), at(ys, upPlane), at(zs, z1)), v(at(xs, x1), at(ys, upPlane), at(zs, z0)))));
        }
    }

    private static boolean[][] faceMask(int uSize, int vSize, CellPredicate predicate) {
        boolean[][] mask = new boolean[uSize][vSize];
        for (int u = 0; u < uSize; u++) {
            for (int v = 0; v < vSize; v++) {
                mask[u][v] = predicate.test(u, v);
            }
        }
        return mask;
    }

    private static void emitRectangles(boolean[][] mask, RectangleEmitter emitter) {
        for (int u0 = 0; u0 < mask.length; u0++) {
            for (int v0 = 0; v0 < mask[u0].length; v0++) {
                if (!mask[u0][v0]) continue;
                int u1 = u0 + 1;
                while (u1 < mask.length && mask[u1][v0]) u1++;
                int v1 = v0 + 1;
                extension:
                while (v1 < mask[u0].length) {
                    for (int u = u0; u < u1; u++) {
                        if (!mask[u][v1]) break extension;
                    }
                    v1++;
                }
                for (int u = u0; u < u1; u++) {
                    for (int v = v0; v < v1; v++) mask[u][v] = false;
                }
                emitter.emit(u0, v0, u1, v1);
            }
        }
    }

    private static float at(List<Float> coordinates, int index) {
        return coordinates.get(index) / 16F;
    }

    @FunctionalInterface
    private interface CellPredicate {
        boolean test(int u, int v);
    }

    @FunctionalInterface
    private interface RectangleEmitter {
        void emit(int u0, int v0, int u1, int v1);
    }

    private static List<Float> coordinates(List<TemplateBox> boxes, Axis axis) {
        return boxes.stream()
                .flatMap(box -> java.util.stream.Stream.of(axis.min(box), axis.max(box)))
                .distinct()
                .sorted()
                .toList();
    }

    private static boolean contains(TemplateBox box, float x, float y, float z) {
        return x > box.minX() && x < box.maxX()
                && y > box.minY() && y < box.maxY()
                && z > box.minZ() && z < box.maxZ();
    }

    private static void addWedge(List<MeshQuad> out, float x0, float x1, float z0, float z1,
                                 float bottomY, float heightAtZ0, float heightAtZ1) {
        out.add(quad(Direction.DOWN,
                v(x0, bottomY, z1), v(x0, bottomY, z0), v(x1, bottomY, z0), v(x1, bottomY, z1)));
        if (heightAtZ0 > bottomY) {
            out.add(quad(Direction.NORTH,
                    v(x1, bottomY, z0), v(x0, bottomY, z0), v(x0, heightAtZ0, z0), v(x1, heightAtZ0, z0)));
        }
        if (heightAtZ1 > bottomY) {
            out.add(quad(Direction.SOUTH,
                    v(x0, bottomY, z1), v(x1, bottomY, z1), v(x1, heightAtZ1, z1), v(x0, heightAtZ1, z1)));
        }
        out.add(quad(Direction.WEST,
                v(x0, bottomY, z0), v(x0, bottomY, z1), v(x0, heightAtZ1, z1), v(x0, heightAtZ0, z0)));
        out.add(quad(Direction.EAST,
                v(x1, bottomY, z1), v(x1, bottomY, z0), v(x1, heightAtZ0, z0), v(x1, heightAtZ1, z1)));
        addSlopeSurface(out, x0, x1, z0, z1, heightAtZ0, heightAtZ1);
    }

    private static void addRoof(List<MeshQuad> out, RoofTemplateForm form, BlockState state) {
        if (form.isRidge()) {
            addRidgeNetwork(out, state);
            return;
        }
        if (form == RoofTemplateForm.GAMBREL) {
            addGambrel(out);
            return;
        }

        StairsShape roofShape = state.hasProperty(SmartRoofTemplateBlock.ROOF_SHAPE)
                ? state.getValue(SmartRoofTemplateBlock.ROOF_SHAPE)
                : StairsShape.STRAIGHT;

        if (roofShape == StairsShape.STRAIGHT) {
            List<PlanPoint> square = List.of(
                    new PlanPoint(0F, 0F), new PlanPoint(0F, 1F),
                    new PlanPoint(1F, 1F), new PlanPoint(1F, 0F));
            addRoofPlane(out, form, square, DistanceAxis.Z);
        } else {
            addCornerRoof(out, form, roofShape);
        }
    }

    private static void addCornerRoof(List<MeshQuad> out, RoofTemplateForm form, StairsShape shape) {
        boolean left = shape == StairsShape.INNER_LEFT || shape == StairsShape.OUTER_LEFT;
        boolean inner = shape == StairsShape.INNER_LEFT || shape == StairsShape.INNER_RIGHT;
        DistanceAxis sideAxis = left ? DistanceAxis.X : DistanceAxis.NEGATIVE_X;
        List<PlanPoint> square = List.of(
                new PlanPoint(0F, 0F), new PlanPoint(0F, 1F),
                new PlanPoint(1F, 1F), new PlanPoint(1F, 0F));

        Scalar branchDifference = point -> DistanceAxis.Z.distance(point) - sideAxis.distance(point);
        List<PlanPoint> zRegion = clip(square, point -> inner
                ? branchDifference.value(point) : -branchDifference.value(point));
        List<PlanPoint> sideRegion = clip(square, point -> inner
                ? -branchDifference.value(point) : branchDifference.value(point));
        addRoofPlane(out, form, zRegion, DistanceAxis.Z);
        addRoofPlane(out, form, sideRegion, sideAxis);
    }

    private static void addRoofPlane(List<MeshQuad> out, RoofTemplateForm form,
                                     List<PlanPoint> region, DistanceAxis slopeAxis) {
        // Split only at changes of pitch. Tile seams belong to the applied
        // material, not a stack of overlapping three-pixel-thick prisms.
        float[] breaks = {0F, form.start(), form.start() + form.run(), 1F};
        for (int i = 0; i < breaks.length - 1; i++) {
            float from = breaks[i], to = breaks[i + 1];
            if (to <= from + 1E-6F) continue;
            List<PlanPoint> band = clip(region, p -> from - slopeAxis.distance(p));
            band = clip(band, p -> slopeAxis.distance(p) - to);
            addRoofRegion(out, band, p -> form.heightAtDistance(slopeAxis.distance(p)));
        }
    }

    private static void addRoofRegion(List<MeshQuad> out, List<PlanPoint> polygon, Scalar height) {
        if (polygon.size() < 3) return;
        float maximum = 0F;
        for (PlanPoint p : polygon) maximum = Math.max(maximum, height.value(p));
        if (maximum < 1E-6F) return;
        PlanPoint origin = com.stardew.craft.port.PortJava.getFirst(polygon);
        for (int i = 1; i < polygon.size() - 1; i++) {
            PlanPoint b = polygon.get(i), c = polygon.get(i + 1);
            Vector3f a3 = v(origin.x, height.value(origin), origin.z);
            Vector3f b3 = v(b.x, height.value(b), b.z);
            Vector3f c3 = v(c.x, height.value(c), c.z);
            if (new Vector3f(b3).sub(a3).cross(new Vector3f(c3).sub(a3)).lengthSquared() < 1E-10F) continue;
            // Surface UVs are tiled at native density after rotation, using
            // the world cell phase supplied by the baked-model wrapper.
            out.add(new MeshQuad(Direction.UP, Direction.UP, List.of(a3, b3, c3, c3),
                    List.of(uv(origin.x, origin.z), uv(b.x, b.z), uv(c.x, c.z), uv(c.x, c.z))));
            out.add(quad(Direction.DOWN, v(c.x, 0F, c.z), v(b.x, 0F, b.z),
                    v(origin.x, 0F, origin.z), v(origin.x, 0F, origin.z)));
        }
        for (int i = 0; i < polygon.size(); i++) {
            PlanPoint a = polygon.get(i), b = polygon.get((i + 1) % polygon.size());
            Direction boundary = boundaryDirection(a, b);
            // Interior pitch/valley seams share vertices; they are not walls.
            if (boundary != null) addBoundary(out, boundary, a.x, a.z, height.value(a),
                    b.x, b.z, height.value(b));
        }
    }

    @Nullable
    private static Direction boundaryDirection(PlanPoint a, PlanPoint b) {
        if (Math.abs(a.x-b.x) + Math.abs(a.z-b.z) < 1E-6F) return null;
        if (a.x < 1E-6F && b.x < 1E-6F) return Direction.WEST;
        if (a.x > 1F-1E-6F && b.x > 1F-1E-6F) return Direction.EAST;
        if (a.z < 1E-6F && b.z < 1E-6F) return Direction.NORTH;
        if (a.z > 1F-1E-6F && b.z > 1F-1E-6F) return Direction.SOUTH;
        return null;
    }

    private static List<PlanPoint> clip(List<PlanPoint> polygon, Scalar boundary) {
        if (polygon.isEmpty()) {
            return polygon;
        }
        ArrayList<PlanPoint> result = new ArrayList<>();
        PlanPoint previous = com.stardew.craft.port.PortJava.getLast(polygon);
        float previousValue = boundary.value(previous);
        boolean previousInside = previousValue <= 1.0E-5F;
        for (PlanPoint current : polygon) {
            float currentValue = boundary.value(current);
            boolean currentInside = currentValue <= 1.0E-5F;
            if (currentInside != previousInside) {
                float amount = Math.max(0F, Math.min(1F, previousValue / (previousValue - currentValue)));
                result.add(new PlanPoint(
                        lerp(amount, previous.x, current.x),
                        lerp(amount, previous.z, current.z)));
            }
            if (currentInside) {
                result.add(current);
            }
            previous = current;
            previousValue = currentValue;
            previousInside = currentInside;
        }
        return List.copyOf(result);
    }

    private static void addBoundary(List<MeshQuad> out, Direction direction,
                                    float x0, float z0, float h0, float x1, float z1, float h1) {
        if (Math.max(h0, h1) < 1E-6F) return;
        out.add(quad(direction, v(x0, h0, z0), v(x0, 0F, z0),
                v(x1, 0F, z1), v(x1, h1, z1)));
    }

    private static void addRidgeNetwork(List<MeshQuad> out, BlockState state) {
        boolean north = state.hasProperty(SmartRidgeTemplateBlock.NORTH) && state.getValue(SmartRidgeTemplateBlock.NORTH);
        boolean east = state.hasProperty(SmartRidgeTemplateBlock.EAST) && state.getValue(SmartRidgeTemplateBlock.EAST);
        boolean south = state.hasProperty(SmartRidgeTemplateBlock.SOUTH) && state.getValue(SmartRidgeTemplateBlock.SOUTH);
        boolean west = state.hasProperty(SmartRidgeTemplateBlock.WEST) && state.getValue(SmartRidgeTemplateBlock.WEST);
        PlanPoint center = new PlanPoint(0.5F, 0.5F);
        PlanPoint[] ring = {new PlanPoint(0,0), new PlanPoint(0,0.5F), new PlanPoint(0,1),
                new PlanPoint(0.5F,1), new PlanPoint(1,1), new PlanPoint(1,0.5F),
                new PlanPoint(1,0), new PlanPoint(0.5F,0)};
        float[] heights = {0, west ? 0.5F : 0, 0, south ? 0.5F : 0,
                0, east ? 0.5F : 0, 0, north ? 0.5F : 0};
        for (int i = 0; i < ring.length; i++) {
            PlanPoint a = ring[i], b = ring[(i+1)%ring.length];
            float ha = heights[i], hb = heights[(i+1)%ring.length];
            addRoofRegion(out, List.of(center, a, b), p -> p == center ? 0.5F : p == a ? ha : hb);
        }
    }

    private static void addGambrel(List<MeshQuad> out) {
        float[] xs = {0, 0.25F, 0.5F, 0.75F, 1};
        for (int i = 0; i < xs.length-1; i++) {
            float x0 = xs[i], x1 = xs[i+1];
            addRoofRegion(out, List.of(new PlanPoint(x0,0), new PlanPoint(x0,1),
                    new PlanPoint(x1,1), new PlanPoint(x1,0)),
                    p -> RoofTemplateForm.GAMBREL.height(p.x, p.z, StairsShape.STRAIGHT));
        }
    }

    private static void addWallBrace(List<MeshQuad> out, boolean descending) {
        float[][] profile = {{0,0}, {0.25F,0}, {1,0.75F}, {1,1}, {0.75F,1}, {0,0.25F}};
        ArrayList<Vector3f> front = new ArrayList<>();
        for (float[] p : profile) front.add(v(p[0], descending ? 1-p[1] : p[1], 0));
        if (!descending) java.util.Collections.reverse(front);
        for (int i = 1; i < front.size()-1; i++) {
            Vector3f a = com.stardew.craft.port.PortJava.getFirst(front), b = front.get(i), c = front.get(i+1);
            out.add(quad(Direction.NORTH, a,b,c,c));
            out.add(quad(Direction.SOUTH, new Vector3f(c).add(0,0,3F/16F),
                    new Vector3f(b).add(0,0,3F/16F), new Vector3f(a).add(0,0,3F/16F),
                    new Vector3f(a).add(0,0,3F/16F)));
        }
        for (int i = 0; i < front.size(); i++) {
            Vector3f a = front.get(i), b = front.get((i+1)%front.size());
            Direction d = Direction.getNearest(-(b.y-a.y), b.x-a.x, 0);
            out.add(quad(d, a, new Vector3f(a).add(0,0,3F/16F),
                    new Vector3f(b).add(0,0,3F/16F), b));
        }
    }

    @FunctionalInterface
    private interface Scalar { float value(PlanPoint point); }
    private record PlanPoint(float x, float z) { }
    private enum DistanceAxis {
        Z { @Override float distance(PlanPoint p) { return p.z; } },
        X { @Override float distance(PlanPoint p) { return p.x; } },
        NEGATIVE_X { @Override float distance(PlanPoint p) { return 1-p.x; } };
        abstract float distance(PlanPoint point);
    }

    /**
     * Keep one material pixel the same physical size on a slope. A full 45 degree
     * slope is sqrt(2) blocks long, so a single 0..1 UV range would visibly
     * stretch the texture. Split long surfaces at whole-block texture intervals
     * and restart the UV range for each interval.
     */
    private static void addSlopeSurface(List<MeshQuad> out, float x0, float x1,
                                        float z0, float z1, float y0, float y1) {
        float dz = z1 - z0;
        float dy = y1 - y0;
        float length = (float) Math.sqrt(dz * dz + dy * dy);
        int segments = Math.max(1, (int) Math.ceil(length - 1.0E-5F));
        for (int segment = 0; segment < segments; segment++) {
            float distance0 = segment;
            float distance1 = Math.min(length, segment + 1F);
            float t0 = distance0 / length;
            float t1 = distance1 / length;
            float segmentZ0 = lerp(t0, z0, z1);
            float segmentZ1 = lerp(t1, z0, z1);
            float segmentY0 = lerp(t0, y0, y1);
            float segmentY1 = lerp(t1, y0, y1);
            out.add(new MeshQuad(
                    Direction.UP,
                    Direction.SOUTH,
                    List.of(
                            v(x0, segmentY0, segmentZ0), v(x0, segmentY1, segmentZ1),
                            v(x1, segmentY1, segmentZ1), v(x1, segmentY0, segmentZ0)),
                    List.of(
                            uv(x0, 0F), uv(x0, distance1 - distance0),
                            uv(x1, distance1 - distance0), uv(x1, 0F))
            ));
        }
    }

    private static float lerp(float amount, float from, float to) {
        return from + amount * (to - from);
    }

    private static MeshQuad quad(Direction direction, Vector3f a, Vector3f b, Vector3f c, Vector3f d) {
        return new MeshQuad(direction, direction, List.of(a, b, c, d), null);
    }

    private static Vector3f v(float x, float y, float z) {
        return new Vector3f(x, y, z);
    }

    private static TexturePoint uv(float u, float v) {
        return new TexturePoint(u, v);
    }

    private enum Axis {
        X {
            @Override float min(TemplateBox box) { return box.minX(); }
            @Override float max(TemplateBox box) { return box.maxX(); }
        },
        Y {
            @Override float min(TemplateBox box) { return box.minY(); }
            @Override float max(TemplateBox box) { return box.maxY(); }
        },
        Z {
            @Override float min(TemplateBox box) { return box.minZ(); }
            @Override float max(TemplateBox box) { return box.maxZ(); }
        };

        abstract float min(TemplateBox box);
        abstract float max(TemplateBox box);
    }

    enum RoofPart { NONE, FIELD, UNDERSIDE, EDGE, LIP, TRIM, CAP }

    record MeshQuad(Direction direction, Direction textureDirection, List<Vector3f> vertices,
                    @Nullable List<TexturePoint> texturePoints, RoofPart part, @Nullable List<TexturePoint> studyPoints) {
        MeshQuad(Direction direction, Direction textureDirection, List<Vector3f> vertices, List<TexturePoint> texturePoints) {
            this(direction, textureDirection, vertices, texturePoints, RoofPart.NONE, null);
        }
        MeshQuad withPart(RoofPart part) {
            return new MeshQuad(direction, textureDirection, vertices, texturePoints, part, studyPoints);
        }
        Direction cullFace() {
            float boundary = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1F : 0F;
            for (Vector3f vertex : vertices) {
                float coordinate = switch (direction.getAxis()) {
                    case X -> vertex.x;
                    case Y -> vertex.y;
                    case Z -> vertex.z;
                };
                if (Math.abs(coordinate - boundary) > 1.0E-5F) {
                    return null;
                }
            }
            return direction;
        }

        MeshQuad transformed(float degrees, boolean flipped) {
            List<Vector3f> result = new ArrayList<>(4);
            float radians = (float) Math.toRadians(degrees);
            float sin = (float) Math.sin(radians);
            float cos = (float) Math.cos(radians);
            for (Vector3f input : vertices) {
                Vector3f point = new Vector3f(input);
                if (flipped) {
                    point.y = 1F - point.y;
                }
                float x = point.x - 0.5F;
                float z = point.z - 0.5F;
                point.x = 0.5F + x * cos - z * sin;
                point.z = 0.5F + x * sin + z * cos;
                result.add(point);
            }
            if (flipped) {
                java.util.Collections.reverse(result);
            }

            int turns = Math.floorMod(Math.round(degrees / 90F), 4);
            Direction transformedDirection = transformDirection(direction, turns, flipped);
            Direction transformedTextureDirection = transformDirection(textureDirection, turns, false);
            List<TexturePoint> transformedTexturePoints = texturePoints == null
                    ? null
                    : new ArrayList<>(texturePoints);
            if (flipped && transformedTexturePoints != null) {
                java.util.Collections.reverse(transformedTexturePoints);
            }
            return new MeshQuad(transformedDirection, transformedTextureDirection, List.copyOf(result),
                    transformedTexturePoints == null ? null : List.copyOf(transformedTexturePoints), part,
                    studyPoints == null ? null : flipped ? com.stardew.craft.port.PortJava.reversed(studyPoints) : studyPoints);
        }

        private static Direction transformDirection(Direction input, int turns, boolean flipped) {
            Direction transformedDirection = input;
            if (flipped) {
                if (transformedDirection == Direction.UP) transformedDirection = Direction.DOWN;
                else if (transformedDirection == Direction.DOWN) transformedDirection = Direction.UP;
            }
            if (transformedDirection.getAxis().isHorizontal()) {
                for (int turn = 0; turn < turns; turn++) {
                    transformedDirection = transformedDirection.getClockWise();
                }
            }
            return transformedDirection;
        }

        @Nullable
        TexturePoint texturePoint(int vertex) {
            return texturePoints == null ? null : texturePoints.get(vertex);
        }
    }

    record TexturePoint(float u, float v) {
    }
}
