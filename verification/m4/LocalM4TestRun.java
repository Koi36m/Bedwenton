import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Set;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
public class LocalM4TestRun {
 public static void main(String[] args) {
  var request = LauncherDiscoveryRequestBuilder.request()
    .selectors(DiscoverySelectors.selectClasspathRoots(Set.of(Path.of(args[0])))).build();
  var listener = new SummaryGeneratingListener();
  var launcher = LauncherFactory.create();
  launcher.registerTestExecutionListeners(listener);
  launcher.execute(request);
  var summary = listener.getSummary();
  var out = new PrintWriter(System.out, true);
  summary.printTo(out); summary.printFailuresTo(out);
  if(summary.getTestsFoundCount()!=313 || summary.getTestsSucceededCount()!=313
    || summary.getTestsSkippedCount()!=0 || summary.getTestsFailedCount()!=0) System.exit(1);
 }
}
