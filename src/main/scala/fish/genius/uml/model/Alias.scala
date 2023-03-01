package fish.genius.uml.model

import java.util.UUID

case class Alias(value: String) {
  require(
    !Alias.regex.r.matches(value),
    "key should be lower case: " + value
  )

  override def toString: String = value
}

object Alias {
  val regex = "[^A-Za-z0-9]"

  def apply(): Alias = apply(UUID.randomUUID().toString)

  def apply(value: String): Alias = new Alias(
    replaceNumbers(value.replaceAll(regex, "").toLowerCase())
  )

  private def replaceNumbers(input: String): String = input
    .replaceAll("-", "")
    .replaceAll("_", "")
    .replaceAll("0", "zero")
    .replaceAll("1", "one")
    .replaceAll("2", "two")
    .replaceAll("3", "three")
    .replaceAll("4", "four")
    .replaceAll("5", "five")
    .replaceAll("6", "six")
    .replaceAll("7", "seven")
    .replaceAll("8", "eight")
    .replaceAll("9", "nine")

}
