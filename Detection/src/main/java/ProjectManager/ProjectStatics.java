package ProjectManager;

import javafx.scene.paint.Color;

/**
 * Created by Alex on 10.01.2018.
 */
public class ProjectStatics
{

    private static int default_cache_size = 44100;
    private static int default_cache_page_size = 2048;
    private static String python_classifier_script_path = "D:\\git\\Licenta\\Discuri\\mlp_prediction_server.py";
    private static String python_classifier_mlp_path = "D:\\git\\Licenta\\Discuri\\pickle.jar";
    private static String python_classifier_scaler_path = "D:\\git\\Licenta\\Discuri\\pickle4scale.jar";
    private static String project_files_path = "C:\\Users\\Alex\\Desktop\\proj_files\\";

    final public static Color signal_color = Color.BLUE;
    final public static Color other_signal_color = Color.GREEN;
    final public static Color marked_signal_color = Color.RED;

    public static int getDefault_cache_page_size()
    {
        return default_cache_page_size;
    }

    public static int getDefault_cache_size()
    {
        return default_cache_size;
    }

    public static String get_test_files_path()
    {
        return "D:\\";
    }

    public static String getPython_classifier_script_path()
    {
        return python_classifier_script_path;
    }

    public static String getPython_classifier_mlp_path()
    {
        return python_classifier_mlp_path;
    }

    public static String getPython_classifier_scaler_path()
    {
        return python_classifier_scaler_path;
    }

    public static String get_temp_files_path()
    {
        return project_files_path;
    }
}
