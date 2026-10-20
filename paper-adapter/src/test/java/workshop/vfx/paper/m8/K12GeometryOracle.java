package workshop.vfx.paper.m8;

import workshop.vfx.math.core.Vec3;
import workshop.vfx.scene.FrameBatch;

/** Independent Bernstein polynomial and trigonometric oracle; never calls production curve/motion evaluators. */
final class K12GeometryOracle {
    private K12GeometryOracle() { }
    static Vec3 local(FrameBatch batch, int i, long age) {
        int n=batch.size();
        if(batch.layer().equals("orbit")){double a=2*Math.PI*i/n;return new Vec3(1.05*Math.cos(a),1.25+1.05*Math.sin(a),-0.85);}
        double a=2*Math.PI*(age%80)/80;
        if(batch.layer().equals("halo")){double b=2*Math.PI*i/n-a;return new Vec3(0.45*Math.cos(b),2.15,-0.45*Math.sin(b));}
        boolean first=batch.layer().equals("stroke-a");
        double[][] p=first?new double[][]{{-.62,-.24},{-.18,-.38},{-.06,-.16},{-.08,.02}}
            :new double[][]{{-.08,.02},{-.12,.38},{.28,.32},{.56,.30}};
        double t=first?(double)i/(n-1):(double)(i+1)/n,u=1-t;
        double x=u*u*u*p[0][0]+3*u*u*t*p[1][0]+3*u*t*t*p[2][0]+t*t*t*p[3][0];
        double y=u*u*u*p[0][1]+3*u*u*t*p[1][1]+3*u*t*t*p[2][1]+t*t*t*p[3][1];
        double scale=1.05/0.78; x*=scale; y*=scale;
        return new Vec3(x*Math.cos(a)-y*Math.sin(a),1.25+x*Math.sin(a)+y*Math.cos(a),-.85);
    }
    static Vec3 world(Vec3 local,double[] player){
        double yaw=Math.toRadians((float)player[3]),c=Math.cos(yaw),s=Math.sin(yaw);
        return new Vec3(player[0]+local.x()*c-local.z()*s,player[1]+local.y(),player[2]+local.x()*s+local.z()*c);
    }
}
