import workshop.vfx.math.core.*;
public class InverseMessage { public static void main(String[] a){
  double[] ss={1e-310, 4.9e-324, 1/Double.MAX_VALUE};
  for(double s:ss){ try{ new Pose(Vec3.ZERO,Quat.IDENTITY,s).inverse(); System.out.println(s+" ok"); } catch(ArithmeticException e){ System.out.println("scale="+s+" 1/s="+(1/s)+" -> "+e.getMessage()); } }
  try{ new Pose(new Vec3(1,0,0),Quat.IDENTITY,1e-310).inverse(); } catch(ArithmeticException e){ System.out.println("t=(1,0,0) scale=1e-310 -> "+e.getMessage()); }
}}
