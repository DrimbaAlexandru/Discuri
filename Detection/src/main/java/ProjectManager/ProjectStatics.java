package ProjectManager;

import javafx.scene.paint.Color;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Created by Alex on 10.01.2018.
 */
public class ProjectStatics
{
    private static int project_cache_size = 1024 * 1024;    /* ALlow up to 2 ^ 20 samples to be cached into*/
    private static int project_cache_page_size = 4096;
    private static int temp_file_cache_size = 1024 * 64;
    private static int temp_file_cache_page_size = 2048;
    private static String python_classifier_script_path = "main.py";
    private static String project_files_path = "D:\\";
    private static String python_scripts_resource_path = null;

    static
    {
        /*
        try
        {
            python_scripts_resource_path = new File( ProjectStatics.class.getClassLoader().getResource( "Python scripts" ).toURI() ).getAbsolutePath() + "/";
        }
        catch( URISyntaxException e )
        {
            e.printStackTrace();
        }
        */
        python_scripts_resource_path = "D:/git/Discuri/Audio_classifier/";

    }

    final public static Color signal_color = Color.BLUE;
    final public static Color other_signal_color = Color.GREEN;
    final public static Color marked_signal_color = Color.RED;

    public static int getProject_cache_page_size()
    {
        return project_cache_page_size;
    }

    public static int getProject_cache_size()
    {
        return project_cache_size;
    }

    public static String get_test_files_path()
    {
        return "D:\\";
    }

    public static String getPython_classifier_script_path()
    {
        return "D:\\git\\Discuri\\Audio_classifier\\main.py";
    }

    public static String getPython_classifier_mlp_path()
    {
        return python_scripts_resource_path + "TBD";
    }

    public static String get_temp_files_path()
    {
        return project_files_path;
    }

    public static URL getFxml_files_path( String filename )
    {
        return ProjectStatics.class.getClassLoader().getResource( "FXML/" + filename );
    }

    public static int getTemp_file_cache_page_size()
    {
        return temp_file_cache_page_size;
    }

    public static int getTemp_file_cache_size()
    {
        return temp_file_cache_size;
    }
}
