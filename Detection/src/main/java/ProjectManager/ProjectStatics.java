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
    private static int project_cache_size = 1024 * 1024;    /* ALlow up to 2 ^ 20 samples to be cached into 256 cache pages */
    private static int project_cache_page_size = 4096;
    private static int temp_file_max_samples = 1024 * 1024; /* Maximum number of samples in temp files */
    private static String python_classifier_script_path = "main.py";
    private static String project_files_path = "E:\\";
    private static String python_scripts_resource_path = null;

    static
    {
        try
        {
            python_scripts_resource_path = new File( ProjectStatics.class.getClassLoader().getResource( "Python scripts" ).toURI() ).getAbsolutePath() + "/";
        }
        catch( URISyntaxException e )
        {
            e.printStackTrace();
        }

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
        return python_scripts_resource_path + python_classifier_script_path;
    }

    public static String get_temp_files_path()
    {
        return project_files_path;
    }

    public static URL getFxml_files_path( String filename )
    {
        return ProjectStatics.class.getClassLoader().getResource( "FXML/" + filename );
    }

    public static int get_temp_file_max_samples()
    {
        return temp_file_max_samples;
    }
}
