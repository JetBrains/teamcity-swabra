

package jetbrains.buildServer.swabra.processes;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: vbedrosova
 * Date: 13.11.10
 * Time: 16:21
 */
public class ProcessInfo {
  @NotNull private final Long myPid;
  @Nullable private final String myName;

  public ProcessInfo(Long pid, String name) {
    myPid = pid;
    myName = name;
  }

  @NotNull
  public Long getPid() {
    return myPid;
  }

  @Nullable
  public String getName() {
    return myName;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final ProcessInfo that = (ProcessInfo)o;

    return (myName == null ? that.myName == null : myName.equals(that.myName)) &&
           myPid.equals(that.myPid);
  }

  @Override
  public int hashCode() {
    int result = myPid.hashCode();
    result = 31 * result + (myName != null ? myName.hashCode() : 0);
    return result;
  }
}