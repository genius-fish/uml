package fish.genius.uml

import org.scalatest.flatspec.AnyFlatSpec

class RendererSpec extends AnyFlatSpec {
  val specification1 =
    """@startuml
      |
      |skin rose
      |
      |title Activity Diagram \n
      |
      |start
      |
      |:Eat Hot Wings;
      |
      |note left
      |    This is a Note...
      |    * Activity diagrams can begin with a Start
      |    * An activity is colon, some words, and a semicolon
      |    * Activity diagrams can end with a stop
      |end note
      |
      |:Drink Homebrew;
      |
      |stop
      |
      |@enduml""".stripMargin

  val specification2 = """@startuml
                         |
                         |!define osaPuml https://raw.githubusercontent.com/Crashedmind/PlantUML-opensecurityarchitecture2-icons/master
                         |!include osaPuml/Common.puml
                         |!include osaPuml/User/all.puml
                         |!include osaPuml/Hardware/all.puml
                         |!include osaPuml/Misc/all.puml
                         |!include osaPuml/Server/all.puml
                         |!include osaPuml/Site/all.puml
                         |
                         |' Users
                         |osa_user_green_developer: <$osa_user_green_developer>
                         |osa_user_green_operations: <$osa_user_green_operations>
                         |osa_user_green_business_manager: <$osa_user_green_business_manager>
                         |
                         |' Devices
                         |osa_desktop: <$osa_desktop>
                         |osa_laptop: <$osa_laptop>
                         |osa_iPhone: <$osa_iPhone>
                         |osa_server: <$osa_server>
                         |
                         |' Network
                         |osa_device_wireless_router: <$osa_device_wireless_router>
                         |osa_hub: <$osa_hub>
                         |osa_firewall: <$osa_firewall>
                         |osa_osa_cloud: <$osa_cloud>
                         |
                         |footer %filename() rendered with PlantUML version %version()\nThe Hitchhiker’s Guide to PlantUML
                         |
                         |@enduml""".stripMargin

}
