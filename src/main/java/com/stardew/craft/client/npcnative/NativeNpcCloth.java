package com.stardew.craft.client.npcnative;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Connected skirt/cape deformation after all skeletal layers, in the garment's attachment space.
 * No simulation clock: identical blended poses produce identical cloth on every client. */
public final class NativeNpcCloth {
    private static final float STEP=.25F;
    private final NativeNpcModel model;
    private final float depthSign;
    private final NativeNpcModel.Cloth settings;
    private final float attachmentFalloff,envelopeFalloff;
    private final NativeNpcSkin hipSkin;
    private final int bone,right,left,control,rows;
    private final int[] legSide;
    private final float[][] rest,legs,otherLeg,shape,envelope,influence;
    private final float[][][] vertices;
    private final Vector3f[] corners={new Vector3f(),new Vector3f(),new Vector3f(),new Vector3f()};
    private final Vector3f point=new Vector3f(),motion=new Vector3f();
    private final Matrix4f inverse=new Matrix4f(),relative=new Matrix4f();
    private float hemLift;

    public NativeNpcCloth(NativeNpcModel model) {
        this.model=model;settings=model.profile().cloth();
        // A three-unit coat hem cannot spend most of its height in a long robe's
        // fixed attachment band. Keep the exact seam fixed, then clear the thighs.
        float length=settings.anchorY()-settings.hemY();
        attachmentFalloff=length<=4?length*.08F:1.4F;
        envelopeFalloff=length<=4?length*.08F:1.2F;
        hipSkin=model.quads().stream().anyMatch(q->hipBound(model,q))?new NativeNpcSkin(model):null;
        // Solve a front apron in mirrored depth space using the same connected-panel constraints.
        depthSign=settings.kind().equals("apron")?-1:1;
        bone=index(settings.bone());right=index("leg_right");left=index("leg_left");control=index("cloth_motion");
        legSide=new int[model.bones().size()];
        for(int i=0;i<legSide.length;i++) {
            int parent=model.bones().get(i).parent();
            legSide[i]=i==right?1:i==left?2:parent>=0?legSide[parent]:0;
        }
        rows=(int)Math.ceil((settings.anchorY()-settings.hemY())/STEP)+1;
        rest=new float[rows][4];legs=new float[rows][4];otherLeg=new float[rows][4];shape=new float[rows][4];envelope=new float[rows][4];
        influence=new float[rows][rows];
        for(int row=0;row<rows;row++)for(int source=0;source<rows;source++) {
            float above=Math.max(0,height(row)-height(source));
            influence[row][source]=(float)Math.exp(-above*above/(2*1.1F*1.1F));
        }
        clear(rest);
        vertices=new float[model.quads().size()][][];
        for(int i=0;i<vertices.length;i++) {
            var q=model.quads().get(i);
            if(q.bone()!=bone)continue;
            vertices[i]=new float[4][3];
            for(int k=0;k<4;k++)corners[k].set(q.vertices()[k][0],q.vertices()[k][1],q.vertices()[k][2]*depthSign);
            if(settings.clearancePart()==null || settings.clearancePart().equals(q.sourcePart())) section(rest,corners);
        }
        // Rows just beyond a rotated hem/attachment use the nearest real cross-section.
        for(int i=0;i<rows;i++)if(!valid(rest[i])) {
            int nearest=-1;
            for(int j=0;j<rows;j++)if(valid(rest[j])&&(nearest<0||Math.abs(j-i)<Math.abs(nearest-i)))nearest=j;
            if(nearest<0)throw new IllegalArgumentException("Empty garment surface");
            System.arraycopy(rest[nearest],0,rest[i],0,4);
        }
        if(settings.kind().equals("skirt")) {
            // Front/back panels can end lower than the side panels. Their shorter hem must not
            // be mistaken for a narrow tube and stretched sideways into a flared lip.
            float minX=Float.POSITIVE_INFINITY,maxX=Float.NEGATIVE_INFINITY;
            for(var row:rest){minX=Math.min(minX,row[0]);maxX=Math.max(maxX,row[1]);}
            for(var row:rest){row[0]=minX;row[1]=maxX;}
        }
    }

    private int index(String name) {
        for(int i=0;i<model.bones().size();i++)if(model.bones().get(i).name().equals(name))return i;
        throw new IllegalArgumentException("Missing cloth bone: "+name);
    }
    public static boolean hipBound(NativeNpcModel model,NativeNpcModel.Quad q) {
        return q.skin()!=null && model.bones().get(q.skin().upper()).name().equals("root")
                && model.bones().get(q.skin().lower()).name().startsWith("leg_");
    }
    private static boolean valid(float[] bounds) { return bounds[0]<=bounds[1]&&bounds[2]<=bounds[3]; }
    private static void clear(float[][] bounds) {
        for(float[] b:bounds){b[0]=b[2]=Float.POSITIVE_INFINITY;b[1]=b[3]=Float.NEGATIVE_INFINITY;}
    }
    private float height(int row) { return Math.min(settings.anchorY(),settings.hemY()+row*STEP); }
    private void section(float[][] bounds,Vector3f[] v) {
        for(int row=0;row<rows;row++) {
            float y=height(row);var b=bounds[row];
            for(int edge=0;edge<4;edge++) {
                var a=v[edge];var z=v[(edge+1)%4];
                if(y<Math.min(a.y,z.y)-.0001F||y>Math.max(a.y,z.y)+.0001F)continue;
                float t=Math.abs(a.y-z.y)<.0001F?0:(y-a.y)/(z.y-a.y);
                float x=a.x+(z.x-a.x)*t,depth=a.z+(z.z-a.z)*t;
                b[0]=Math.min(b[0],x);b[1]=Math.max(b[1],x);
                b[2]=Math.min(b[2],depth);b[3]=Math.max(b[3],depth);
            }
        }
    }
    private void legSection(float[][] bounds,Vector3f[] v) {
        for(int row=0;row<rows;row++) {
            float y=liftedHeight(height(row));var b=bounds[row];
            for(int edge=0;edge<4;edge++) {
                var a=v[edge];var z=v[(edge+1)%4];
                float t=Math.abs(a.y-z.y)<.0001F?0:Math.max(0,Math.min(1,(y-a.y)/(z.y-a.y)));
                // Extremes of position +/- vertical distance occur at an endpoint or the
                // plane intersection. Keeping all three avoids losing support near a level edge.
                for(int candidate=0;candidate<3;candidate++) {
                    float sample=candidate==0?0:candidate==1?t:1;
                    float x=a.x+(z.x-a.x)*sample,depth=a.z+(z.z-a.z)*sample;
                    float dy=a.y+(z.y-a.y)*sample-y;
                    // Round the corner crossing a garment row so articulated knees do not
                    // introduce a sharp contact derivative when an edge becomes horizontal.
                    float distance=1.5F*((float)Math.sqrt(dy*dy+.04F)-.2F);
                    b[0]=Math.min(b[0],x+distance);b[1]=Math.max(b[1],x-distance);
                    b[2]=Math.min(b[2],depth+distance);b[3]=Math.max(b[3],depth-distance);
                }
            }
        }
    }

    private float liftedHeight(float y) {
        return y+hemLift*smooth((settings.anchorY()-y)/(settings.anchorY()-settings.hemY()));
    }
    private static float smooth(float t) { t=Math.max(0,Math.min(1,t));return t*t*(3-2*t); }
    private static float maximum(float a,float b) {
        // Rounded maximum prevents hard changes when the leading leg switches.
        float delta=a-b;return .5F*(a+b+(float)Math.sqrt(delta*delta+.01F));
    }

    private static float contactMaximum(float a,float b) {
        float overlap=Math.max(0,.20F-Math.abs(a-b));
        return Math.max(a,b)+overlap*overlap/.80F;
    }

    /** Returns world/model-space vertices only for cloth quads; arrays are reused until the next update. */
    public float[][][] update(Matrix4f[] transforms) {
        var hipSurface=hipSkin==null?null:hipSkin.update(transforms,null);
        inverse.set(transforms[bone]).invert();clear(legs);clear(otherLeg);
        relative.set(inverse).mul(transforms[control]);relative.transformPosition(motion.set(0,0,0));
        // Optional activity-authored fold: preserve row order and solve contacts at lifted heights.
        hemLift=settings.supportLift()?Math.clamp(motion.y,0,(settings.anchorY()-settings.hemY())*.49F):0;
        float blend=settings.contactBlend()==null?0:settings.contactBlend();
        for(int qi=0;qi<model.quads().size();qi++)if(legSide[model.quads().get(qi).bone()]!=0) {
            var q=model.quads().get(qi);
            relative.set(inverse).mul(transforms[q.bone()]);
            for(int k=0;k<4;k++) {
                if(hipBound(model,q))inverse.transformPosition(corners[k].set(hipSurface[qi][k]));
                else relative.transformPosition(corners[k].set(q.vertices()[k][0],q.vertices()[k][1],q.vertices()[k][2]));
            }
            for(var corner:corners)corner.z*=depthSign;
            legSection(blend>0 && legSide[q.bone()]==2?otherLeg:legs,corners);
        }
        // An ankle-length hem sees both feet pass one another. Blend their contact bounds
        // before spreading them down the garment, so the leading foot cannot switch sharply.
        if(blend>0)for(int row=0;row<rows;row++)for(int k=0;k<4;k++) {
            float sign=k%2==0?-1:1;
            float a=legs[row][k]*sign,b=otherLeg[row][k]*sign;
            float overlap=Math.max(0,blend-Math.abs(a-b));
            legs[row][k]=sign*(Math.max(a,b)+overlap*overlap/(4*blend));
        }
        relative.set(inverse).mul(transforms[control]);relative.transformPosition(motion.set(0,0,0));
        motion.z*=depthSign;
        // A shoulder mantle wraps front/side/back but remains open. Its front and back
        // must clear independently; a rear-only cape would push the front through the arm.
        boolean skirt=settings.kind().equals("skirt") || settings.kind().equals("mantle");
        float contactMargin=settings.margin();
        if(skirt) {
            float swing=0,firstHipY=Float.NaN;
            for(int leg:new int[]{right,left}) {
                relative.set(inverse).mul(transforms[leg]);
                swing=Math.max(swing,Math.abs(relative.m12()));
                relative.transformPosition(point.set(model.bones().get(leg).origin()));
                if(Float.isNaN(firstHipY))firstHipY=point.y;
                else swing=Math.max(swing,.5F*Math.abs(point.y-firstHipY));
            }
            // Quiet standing needs a close fit; retain the full swept-motion allowance for steps.
            float standing=settings.standingDepthMargin()==null?contactMargin:settings.standingDepthMargin();
            contactMargin=standing+(contactMargin-standing)*smooth((swing-.06F)/.20F);
        }
        for(int row=0;row<rows;row++) {
            float free=smooth((settings.anchorY()-height(row))/(settings.anchorY()-settings.hemY()));
            float[] r=rest[row],s=shape[row];
            s[0]=r[0]+motion.x*free;s[1]=r[1]+motion.x*free;
            s[2]=r[2]+motion.z*free;s[3]=r[3]+motion.z*free;
            // Use neighbouring slices as well: generated strips must clear the leg between their end rows.
            for(int j=Math.max(0,row-2);j<=Math.min(rows-1,row+2);j++) {
                float[] l=legs[j];float margin=contactMargin;
                if(skirt) {
                    s[0]=-contactMaximum(-s[0],-l[0]+settings.margin());s[1]=contactMaximum(s[1],l[1]+settings.margin());
                    s[2]=-contactMaximum(-s[2],-l[2]+margin);s[3]=contactMaximum(s[3],l[3]+margin);
                } else {
                    // The robe/cape hangs behind both legs, with the upper shoulder edge fixed.
                    float back=maximum(s[2],l[3]+margin);
                    s[3]+=back-s[2];s[2]=back;
                }
            }
            // Do not stretch or detach the attachment edge above the legs.
            float attached=smooth((settings.anchorY()-height(row))/attachmentFalloff);
            for(int k=0;k<4;k++)s[k]=r[k]+(s[k]-r[k])*attached;
        }
        // Spread local contact below its height rather than applying one displacement to every row.
        for(int row=0;row<rows;row++) {
            shape[row][0]=Math.max(0,rest[row][0]-shape[row][0]);
            shape[row][1]=Math.max(0,shape[row][1]-rest[row][1]);
            shape[row][2]=skirt?Math.max(0,rest[row][2]-shape[row][2]):0;
            shape[row][3]=Math.max(0,shape[row][3]-rest[row][3]);
        }
        for(int row=0;row<rows;row++)for(int k=0;k<4;k++) {
            float value=0;
            for(int source=0;source<rows;source++) {
                value=Math.max(value,shape[source][k]*influence[row][source]);
            }
            // Smooth the contact rows as the supporting leg changes.
            float sum=0;
            for(int source=0;source<rows;source++)sum+=(float)Math.exp((shape[source][k]*influence[row][source]-value)/.20F);
            // A mean, not a sum: repeated contact rows must not inflate the whole garment.
            // Contact margins are measured against final opaque surfaces after this smoothing.
            value=Math.max(0,value+.20F*(float)Math.log(skirt?sum/rows:sum));
            envelope[row][k]=value*smooth((settings.anchorY()-height(row))/envelopeFalloff);
        }
        for(int row=0;row<rows;row++) {
            float free=smooth((settings.anchorY()-height(row))/(settings.anchorY()-settings.hemY()));
            shape[row][0]=rest[row][0]-(skirt?envelope[row][0]:0);
            shape[row][1]=rest[row][1]+(skirt?envelope[row][1]:0);
            shape[row][2]=rest[row][2]+(skirt?-envelope[row][2]:envelope[row][3]);
            shape[row][3]=rest[row][3]+envelope[row][3];
            if(!skirt){shape[row][0]+=motion.x*free;shape[row][1]+=motion.x*free;}
        }
        for(int i=0;i<vertices.length;i++)if(vertices[i]!=null) {
            var q=model.quads().get(i);
            for(int k=0;k<4;k++) {
                var v=q.vertices()[k];point.set(v[0],v[1],v[2]*depthSign);
                float at=Math.max(0,Math.min(rows-1,(point.y-settings.hemY())/STEP));
                int low=(int)at,high=Math.min(rows-1,low+1);float t=at-low;
                if(point.y<settings.anchorY()) {
                    float rx=lerp(rest,low,high,t,0),rw=lerp(rest,low,high,t,1)-rx;
                    float rz=lerp(rest,low,high,t,2),rd=lerp(rest,low,high,t,3)-rz;
                    float sx=lerp(shape,low,high,t,0),sw=lerp(shape,low,high,t,1)-sx;
                    float sz=lerp(shape,low,high,t,2),sd=lerp(shape,low,high,t,3)-sz;
                    if(skirt) {
                        if(settings.preserveLayerOffset()) {
                            // Decorative layers retain their original distance outside the opaque core.
                            // Extrapolating the core scale would magnify a thin fold into a wide hem.
                            float x=Math.max(rx,Math.min(rx+rw,point.x)),z=Math.max(rz,Math.min(rz+rd,point.z));
                            point.x=sx+(x-rx)*sw/Math.max(rw,.001F)+(point.x-x);
                            point.z=sz+(z-rz)*sd/Math.max(rd,.001F)+(point.z-z);
                        } else {
                            point.x=sx+(point.x-rx)*sw/Math.max(rw,.001F);
                            point.z=sz+(point.z-rz)*sd/Math.max(rd,.001F);
                        }
                    } else {point.x+=sx-rx;point.z+=sz-rz;}
                }
                point.z*=depthSign;
                point.y=liftedHeight(point.y);
                transforms[bone].transformPosition(point);
                vertices[i][k][0]=point.x;vertices[i][k][1]=point.y;vertices[i][k][2]=point.z;
            }
        }
        return vertices;
    }
    private static float lerp(float[][] a,int low,int high,float t,int k) {return a[low][k]+(a[high][k]-a[low][k])*t;}
}
