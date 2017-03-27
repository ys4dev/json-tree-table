import javafx.application.Application
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.scene.layout.Pane
import javafx.stage.Stage

/**
 *
 */

class MyApp : Application() {
    override fun start(primaryStage: Stage?) {
        val pane = FXMLLoader.load<Pane>(javaClass.getResource("Main.fxml"))
        primaryStage?.scene = Scene(pane)
        primaryStage?.show()
    }
}

fun main(args: Array<String>) {
    Application.launch(MyApp::class.java, *args)
}

