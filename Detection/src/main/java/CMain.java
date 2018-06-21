import GUI.Main_window;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * Created by Alex on 12.03.2017.
 */

public class CMain extends Application {

    public void start( Stage stage )
    {
        Main_window mw = new Main_window();
        mw.run();
    }

    public static void main( String[] args )
    {
        launch( args );
    }

    /**TODO:
     *  - add the generate marking functionality to the UI
     *  - work on the UI
     */
}
