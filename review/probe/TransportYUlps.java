import workshop.vfx.math.core.*;
import java.util.SplittableRandom;
public class TransportYUlps {
  static Vec3 unitRand(SplittableRandom r){while(true){double x=r.nextDouble(-1,1),y=r.nextDouble(-1,1),z=r.nextDouble(-1,1);double l=Math.sqrt(x*x+y*y+z*z);if(l>0.1&&l<=1)return new Vec3(x/l,y/l,z/l);}}
  static long ulps(double a,double b){return Math.abs(Double.doubleToLongBits(a)-Double.doubleToLongBits(b));}
  public static void main(String[] a){
    SplittableRandom r=new SplittableRandom(7); long maxT=0,maxH=0; double absT=0,absH=0; int nT=0,nH=0, N=1_000_000;
    for(int i=0;i<N;i++){ Vec3 v=unitRand(r).mul(Math.pow(10,r.nextDouble(-11,300)));
      Vec3 yn=v.normalizedOr(null); Frame g=Frame.transportY(Frame.WORLD,v);
      long u=Math.max(ulps(g.y().x(),yn.x()),Math.max(ulps(g.y().y(),yn.y()),ulps(g.y().z(),yn.z())));
      if(u>0)nT++; maxT=Math.max(maxT,u); absT=Math.max(absT,g.y().sub(yn).length());
      Frame h=Frame.fromHeading(v,Vec3.Y); Vec3 back=yn.negate();
      long w=Math.max(ulps(h.z().x(),back.x()),Math.max(ulps(h.z().y(),back.y()),ulps(h.z().z(),back.z())));
      if(w>0)nH++; maxH=Math.max(maxH,w); absH=Math.max(absH,h.z().sub(back).length()); }
    System.out.printf("transportY: y != normalize(newY) bitwise in %d/%d cases, max %d ulp, max |diff| %.3e%n",nT,N,maxT,absT);
    System.out.printf("fromHeading: z != -normalize(forward) bitwise in %d/%d cases, max %d ulp, max |diff| %.3e%n",nH,N,maxH,absH);
    Vec3 ex=new Vec3(0.1,0.2,0.3); System.out.println("example newY="+ex+" normalize="+ex.normalizedOr(null)+" transportY(WORLD,.).y="+Frame.transportY(Frame.WORLD,ex).y());
  }
}
