import java.util.regex.Matcher
import java.util.regex.Pattern

case class SemanticVersioning(version: String) {

  private val versionPattern: Pattern =
    Pattern.compile("([1-9]\\d*)\\.(\\d+)\\.(\\d+)(?:-([a-zA-Z0-9]+))?")
  private val matcher: Matcher = versionPattern.matcher(version)

  def majorMinor = {
    require(matcher.matches())
    s"${matcher.group(1)}.${matcher.group(2)}"
  }

}
