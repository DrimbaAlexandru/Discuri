package GUI;

import AudioDataSource.AudioSamplesWindow;
import Exceptions.DataSourceException;
import GUI.UI_Components.Effect_Input_Dialogs.*;
import GUI.UI_Components.Effect_Progress_Bar_Dialog;
import GUI.UI_Components.Export_Progress_Bar_Dialog;
import MarkerFile.Marking;
import ProjectManager.*;
import SignalProcessing.Effects.*;
import SignalProcessing.Filters.FIR;
import SignalProcessing.Filters.IIR;
import Utils.DataSetGenerator;
import Utils.Interval;
import com.sun.istack.internal.Nullable;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.io.*;
import java.text.ParseException;

import static Utils.Util_Stuff.remap_to_interval;

/**
 * Created by Alex on 27.03.2017.
 */
public class Main_window
{
    /*************************************************
    Class fields
    *************************************************/
    /*--------------------------------
    FXML variables
    --------------------------------*/
    Stage localStage = new Stage();

    private Pane mainLayout = null;
    private Scene localScene;
    @FXML
    private Button btn_prev_frame, btn_prev_sample, btn_next_frame, btn_next_sample, btn_redo, btn_undo;
    @FXML
    private Button btn_zoom_in, btn_zoom_out, btn_mark_sample, btn_expand_select, btn_constrict_select;
    @FXML
    private AnchorPane pnl_window;
    @FXML
    private Canvas main_canvas;
    @FXML
    private Spinner current_sample_spinner;
    @FXML
    private Spinner sel_len_spinner;
    @FXML
    private Label position_indicator;
    @FXML
    private Label sel_len_indicator;
    @FXML
    private ScrollBar time_scroll;
    @FXML
    private MenuItem menu_open_file, menu_export, menu_export_selected, menu_load_marker, menu_save_marker, menu_close;
    @FXML
    private Menu menu_effects, menu_markings, menu_file;

    /*--------------------------------
    Position, selection and audio data variables
    --------------------------------*/
    private int zoom_index = 0;
    private int window_size = 32768;
    private int first_sample_index = 0;

    private final Interval selection = new Interval( 0, 0 );
    private int selection_started_index = 0;

    private AudioSamplesWindow visible_samples = null;
    private Interval visible_samples_interval = new Interval( 0, 0 );

    private int sample_number;
    private int channel_number;
    private int sample_rate;

    /*--------------------------------
    GUI related variables
    --------------------------------*/
    private int window_left_pad = 25;
    private int display_window_height;
    private int display_window_width;

    private boolean run_updater = true;
    private boolean is_updater_suspended = false;
    private boolean main_canvas_LMB_down_on_samples;
    private boolean main_canvas_LMB_down_on_zoom;
    private int periodic_window_increment = 0;

    private boolean window_size_changed, selection_changed, displayed_interval_changed, markings_changed; /* Flags for updating selection and samples related GUI components

    /*************************************************
    UI-related methods
    *************************************************/

    private void treatException( Exception e )
    {
        e.printStackTrace();
        if( e instanceof DataSourceException )
        {
            showDialog( Alert.AlertType.ERROR, e.getMessage(), ( ( DataSourceException )e ).getDSEcause().name().replace( '_', ' ' ) );
        }
        else
        {
            showDialog( Alert.AlertType.ERROR, e.getMessage(), "Error" );
        }
    }

    private void showDialog( Alert.AlertType type, String message, String title )
    {
        is_updater_suspended = true;
        Alert alert = new Alert( type );
        alert.setTitle( title );
        alert.setHeaderText( null );
        alert.setContentText( message );
        alert.showAndWait();
        is_updater_suspended = false;
    }

    /*----------------------------------------
    Method name: refresh_view()
    Description: Refresh the GUI to match the current
                 position, selection etc.
    ----------------------------------------*/
    private void refresh_view() throws DataSourceException
    {
        if( selection_changed || displayed_interval_changed || window_size_changed || markings_changed )
        {
            drawSamples();
        }
        if( selection_changed )
        {
            refreshSelection();
        }
    } /* refresh_view */

    /*----------------------------------------
    Method name: drawSamples
    Description: Refresh the audio-samples related
                 GUI components
    ----------------------------------------*/
    private void drawSamples()
    {
        /*--------------------------------
        Local variables
        --------------------------------*/
        int i;
        int display_window_size;
        AudioSamplesWindow win;
        final GraphicsContext gc = main_canvas.getGraphicsContext2D();
        Interval visible_selection = new Interval( selection.l, selection.r, false );
        visible_selection.limit( first_sample_index, first_sample_index + window_size );

        try
        {
            ProjectManager.lock_access();

            /* Handle no WAV file loaded case */
            if( ProjectManager.getCache() == null )
            {
                return;
            }

            /*--------------------------------
            Initialize variables
            --------------------------------*/
            if( window_size <= display_window_width )
            {
                display_window_size = window_size;
            }
            else
            {
                display_window_size = 2 * display_window_width;
            }

            //Update the visible_samples
            if( window_size_changed || displayed_interval_changed )
            {

                if( !visible_samples_interval.includes( new Interval( first_sample_index, window_size ) ) || ( window_size_changed  ) )
                {
                    visible_samples = ProjectManager.getCache().get_resized_samples( first_sample_index, window_size, display_window_size );
                    visible_samples_interval.l = first_sample_index;
                    visible_samples_interval.r = first_sample_index + window_size;
                }

                window_size_changed = false;
            }

            /*--------------------------------
            Redraw the canvas
            --------------------------------*/
            gc.setFill( Color.WHITE );
            gc.fillRect( 0, 0, main_canvas.getWidth(), main_canvas.getHeight() );

            gc.setFill( new Color( 0, 0.5, 1, 0.1 ) );
            gc.fillRect( window_left_pad + remap_to_interval( visible_selection.l - first_sample_index, 0, window_size, 0, display_window_width ), 0, remap_to_interval( visible_selection.get_length(), 0, window_size, 0, display_window_width ), main_canvas.getHeight() );

            gc.setStroke( Color.BLACK );
            gc.strokeLine( window_left_pad, 0, window_left_pad, main_canvas.getHeight() );
            gc.strokeLine( window_left_pad, main_canvas.getHeight() / 2, main_canvas.getWidth(), main_canvas.getHeight() / 2 );
            gc.strokeRect( 0, 0, main_canvas.getWidth(), main_canvas.getHeight() );


            for( i = 0; i < display_window_size - 1; i++ )
            {
                /* Draw L channel */
                if( channel_number >= 1 )
                {
                    if( ProjectManager.getMarkerFile().isMarked( remap_to_interval( i, 0, display_window_size, first_sample_index, first_sample_index + window_size ), 0 ) )
                    {
                        gc.setStroke( ProjectStatics.marked_signal_color );
                    }
                    else
                    {
                        gc.setStroke( ProjectStatics.signal_color );
                    }
                    gc.strokeLine( i * display_window_width / ( display_window_size - 1 ) + window_left_pad, ( 1 + Math.min( Math.max( -visible_samples.getSamples()[ 0 ][ i ] * Math.pow( 2, zoom_index ), -1 ), 1 ) ) * display_window_height / 2, ( i + 1 ) * display_window_width / ( display_window_size - 1 ) + window_left_pad, ( 1 + Math.min( Math.max( -visible_samples.getSamples()[ 0 ][ i + 1 ] * Math.pow( 2, zoom_index ), -1 ), 1 ) ) * display_window_height / 2 );
                }
                /* Draw R channel */
                if( channel_number >= 2 )
                {
                    if( ProjectManager.getMarkerFile().isMarked( remap_to_interval( i, 0, display_window_size, first_sample_index, first_sample_index + window_size ), 1 ) )
                    {
                        gc.setStroke( ProjectStatics.marked_signal_color );
                    }
                    else
                    {
                        gc.setStroke( ProjectStatics.other_signal_color );
                    }
                    gc.strokeLine( i * display_window_width / ( display_window_size - 1 ) + window_left_pad, ( 1 + Math.min( Math.max( -visible_samples.getSamples()[ 1 ][ i ] * Math.pow( 2, zoom_index ), -1 ), 1 ) ) * display_window_height / 2, ( i + 1 ) * display_window_width / ( display_window_size - 1 ) + window_left_pad, ( 1 + Math.min( Math.max( -visible_samples.getSamples()[ 1 ][ i + 1 ] * Math.pow( 2, zoom_index ), -1 ), 1 ) ) * display_window_height / 2 );
                }
            }
            main_canvas.getGraphicsContext2D().setFill( Color.BLACK );
            displayed_interval_changed = false;
            markings_changed = false;
        }
        catch( DataSourceException e )
        {
            treatException( e );
        }
        finally
        {
            ProjectManager.release_access();
        }
    } /* drawSamples */

    /*----------------------------------------
    Method name: refreshSelection
    Description: Update selection related UI controls
    ----------------------------------------*/
    private void refreshSelection()
    {
        if( sample_rate == 0 )
        {
            return;
        }
        final int sel_len = selection.get_length();
        final int sel_start_seconds = selection.l / sample_rate;
        final int sel_start_milliseconds = ( selection.l % sample_rate * 1000 ) / sample_rate;
        final int sel_end_seconds = selection.r / sample_rate;
        final int sel_end_millisecods = ( selection.r % sample_rate * 1000 ) / sample_rate;

        time_scroll.setValue( selection.l );
        position_indicator.setText( "samples / " + ( sample_number ) +
                                            " ( " + String.format( "%02d", sel_start_seconds / 3600 ) +
                                            ":" + String.format( "%02d", sel_start_seconds / 60 % 60 ) +
                                            ":" + String.format( "%02d", sel_start_seconds % 60 ) +
                                            "." + String.format( "%03d", sel_start_milliseconds ) +
                                            " / " + String.format( "%02d", ( sample_number / sample_rate ) / 3600 ) +
                                            ":" + String.format( "%02d", sample_number / sample_rate / 60 % 60 ) +
                                            ":" + String.format( "%02d", sample_number / sample_rate % 60 ) +
                                            "." + String.format( "%03d", ( sample_number % sample_rate * 1000 ) / sample_rate ) + " )" );
        current_sample_spinner.getValueFactory().setValue( selection.l );
        sel_len_indicator.setText( "samples ( "+
                                           String.format( "%02d", sel_len / sample_rate / 3600 ) +
                                           ":" + String.format( "%02d", sel_len / sample_rate / 60 % 60 ) +
                                           ":" + String.format( "%02d", sel_len / sample_rate % 60 ) +
                                           "." + String.format( "%03d", ( sel_len % sample_rate * 1000 ) / sample_rate ) + " )" );
        sel_len_spinner.getValueFactory().setValue( sel_len );
        time_scroll.setBlockIncrement( window_size );
        time_scroll.setValue( first_sample_index );
        selection_changed = false;
    }

    /*----------------------------------------
    Method name: Main_window
    Description: Default class constructor. Loads the
                 GUI and starts the application
    ----------------------------------------*/
    public Main_window()
    {
        FXMLLoader l = new FXMLLoader();
        l.setController( this );
        l.setLocation( ProjectStatics.getFxml_files_path( "fxml_main.fxml" ) );

        try
        {
            mainLayout = l.load();
            display_window_height = ( int )main_canvas.getHeight();
            display_window_width = ( int )main_canvas.getWidth() - window_left_pad;
            startRefresher();

            menu_open_file.setOnAction( this::on_load_audio );
            menu_load_marker.setOnAction( this::on_load_marker );
            menu_save_marker.setOnAction( this::on_export_marker );
            menu_export.setOnAction( ev ->
                                     {
                                         on_export_project( false );
                                     } );
            menu_export_selected.setOnAction( ev ->
                                              {
                                                  on_export_project( true );
                                              } );
            menu_close.setOnAction( ev ->
                                    {
                                        on_close_application();
                                        localStage.close();
                                    } );

            btn_redo.setOnAction( this::on_redo );
            btn_undo.setOnAction( this::on_undo );

            time_scroll.setUnitIncrement( 1 );

            time_scroll.setOnMouseClicked( e ->
                                           {
                                               set_first_sample_index( ( int )time_scroll.getValue() );
                                           } );

            time_scroll.setOnMouseReleased( e ->
                                            {
                                                set_first_sample_index( ( int )time_scroll.getValue() );
                                            } );

            btn_next_frame.setOnMouseClicked( e ->
                                              {
                                                  set_first_sample_index( first_sample_index + window_size );
                                              } );
            btn_next_sample.setOnMouseClicked( e ->
                                               {
                                                   set_first_sample_index( first_sample_index + ( window_size + display_window_width - 1 ) / display_window_width );
                                               } );
            btn_prev_frame.setOnMouseClicked( e ->
                                              {
                                                  set_first_sample_index( first_sample_index - window_size );
                                              } );
            btn_prev_sample.setOnMouseClicked( e ->
                                               {
                                                   set_first_sample_index( first_sample_index - ( window_size + display_window_width - 1 ) / display_window_width );

                                               } );
            btn_constrict_select.setOnMouseClicked( e ->
                                                    {
                                                        window_size /= 2;
                                                        if( window_size < 4 )
                                                        {
                                                            window_size = 4;
                                                        }
                                                        window_size_changed = true;
                                                        set_first_sample_index( selection.l + selection.get_length() / 2 - window_size / 2 );
                                                    } );
            btn_expand_select.setOnMouseClicked( e ->
                                                 {
                                                     window_size *= 2;
                                                     if( window_size > sample_number )
                                                     {
                                                         window_size = sample_number;
                                                     }
                                                     window_size_changed = true;
                                                     set_first_sample_index( first_sample_index - window_size / 4 );
                                                 } );
            btn_zoom_in.setOnMouseClicked( e ->
                                           {
                                               zoom_index++;
                                               displayed_interval_changed = true;
                                           } );
            btn_zoom_out.setOnMouseClicked( e ->
                                            {
                                                zoom_index--;
                                                displayed_interval_changed = true;
                                            } );
            main_canvas.setOnMousePressed( e ->
                                           {
                                               System.out.println( "Pressed" );
                                               main_canvas_LMB_down_on_samples = false;
                                               main_canvas_LMB_down_on_zoom = false;
                                               if( e.getX() >= window_left_pad && e.getX() <= main_canvas.getWidth() ) /* Selection change handling */
                                               {
                                                   selection_started_index = remap_to_interval( ( int )e.getX() - window_left_pad, 0, display_window_width, first_sample_index, first_sample_index + window_size );
                                                   selection.r = selection.l = selection_started_index;
                                                   selection_changed = true;
                                                   main_canvas_LMB_down_on_samples = true;
                                               }
                                               if( e.getX() < window_left_pad && e.getX() >= 0 ) /* Zoom change handling */
                                               {
                                                   main_canvas_LMB_down_on_zoom = true;
                                               }

                                           } );
            main_canvas.setOnMouseReleased( e ->
                                            {
                                                System.out.println( "Released" );
                                                periodic_window_increment = 0;
                                            } );

            main_canvas.setOnMouseDragged( e ->
                                           {
                                               if( main_canvas_LMB_down_on_samples ) /* Selection change handling */
                                               {
                                                   periodic_window_increment = 0;
                                                   selection_changed = true;
                                                   int cursor_position = remap_to_interval( ( int )e.getX() - window_left_pad, 0, display_window_width, first_sample_index, first_sample_index + window_size );

                                                   if( cursor_position < selection_started_index )
                                                   {
                                                       selection.l = cursor_position;
                                                       selection.r = selection_started_index;
                                                   }
                                                   else
                                                   {
                                                       selection.l = selection_started_index;
                                                       selection.r = cursor_position;
                                                   }
                                                   if( e.getX() < window_left_pad )
                                                   {
                                                       periodic_window_increment = ( int )( e.getX() - window_left_pad ) * window_size / display_window_width;
                                                   }
                                                   if( e.getX() > main_canvas.getWidth() )
                                                   {
                                                       periodic_window_increment = ( int )( e.getX() - main_canvas.getWidth() ) * window_size / display_window_width;
                                                   }
                                               }
                                               else /* Vertical zoom handling */
                                               {
                                                   ;
                                               }

                                           } );

            MenuItem mi;
            mi = new MenuItem( "Amplify" );
            menu_effects.getItems().add( mi );
            mi.setOnAction( this::on_amplify );

            mi = new MenuItem( "Repair selected markings" );
            menu_effects.getItems().add( mi );
            mi.setOnAction( this::on_Repair );

            mi = new MenuItem( "Equalizer" );
            menu_effects.getItems().add( mi );
            mi.setOnAction( this::on_equalizer );

            mi = new MenuItem( "Discrete derivation" );
            menu_effects.getItems().add( mi );
            mi.setOnAction( this::on_derivation );

            mi = new MenuItem( "Discrete integration" );
            menu_effects.getItems().add( mi );
            mi.setOnAction( this::on_integration );

            mi = new MenuItem( "Groove retracking" );
            menu_effects.getItems().add( mi );
            mi.setOnAction( this::on_groove_retracking );

            mi = new MenuItem( "Mark selection" );
            menu_markings.getItems().add( mi );
            mi.setOnAction( this::on_add_marking );

            mi = new MenuItem( "Unmark selection" );
            menu_markings.getItems().add( mi );
            mi.setOnAction( this::on_remove_marking );

            mi = new MenuItem( "Clear all markings" );
            menu_markings.getItems().add( mi );
            mi.setOnAction( this::on_clear_markings );

            mi = new MenuItem( "Generate markings" );
            menu_markings.getItems().add( mi );
            mi.setOnAction( this::on_generate_markings );

            mi = new MenuItem( "Generate dataset" );
            menu_file.getItems().add( mi );
            mi.setOnAction( this::on_generate_dataset );
        }
        catch( IOException e )
        {
            treatException( e );
        }
    } /* Main_window */

    /*----------------------------------------
    Method name: run()
    Description: JavaFX method for application init.
    ----------------------------------------*/
    public void run()
    {
        if( mainLayout == null )
        {
            Alert a = new Alert( Alert.AlertType.ERROR, "Failed to load FXML file." );
            a.show();
            return;
        }

        Group localroot = new Group();

        localScene = new Scene( localroot, mainLayout.getPrefWidth(), mainLayout.getPrefHeight(), Color.color( 1, 1, 1 ) );
        localStage.setResizable( false );
        localStage.setScene( localScene );
        localroot.getChildren().add( mainLayout );
        localStage.show();
        //refresh_view();
        localScene.setOnKeyReleased( e ->
                                     {
                                         //Handle key shortcuts
                                     } );
        localStage.setOnCloseRequest( ev ->
                                      {
                                          on_close_application();
                                      } );

    } /* run() */

    private void startRefresher()
    {
        Thread th = new Thread( () ->
                                {
                                    while( run_updater )
                                    {
                                        if( !is_updater_suspended )
                                        {
                                            Platform.runLater( () ->
                                                               {
                                                                   if( is_updater_suspended )
                                                                   {
                                                                       return;
                                                                   }
                                                                   if( periodic_window_increment != 0 )
                                                                   {
                                                                       if( periodic_window_increment > 0 )
                                                                       {
                                                                           periodic_window_increment = Math.min( periodic_window_increment, window_size / 2 );
                                                                           set_first_sample_index( first_sample_index + periodic_window_increment );
                                                                           selection.r = first_sample_index + window_size;
                                                                           selection_changed = true;
                                                                       }
                                                                       else
                                                                       {
                                                                           periodic_window_increment = Math.max( periodic_window_increment, -window_size / 2 );
                                                                           set_first_sample_index( first_sample_index + periodic_window_increment );
                                                                           selection.l = first_sample_index;
                                                                           selection_changed = true;
                                                                       }
                                                                   }
                                                                   try
                                                                   {
                                                                       refresh_view();
                                                                   }
                                                                   catch( DataSourceException e )
                                                                   {
                                                                       treatException( e );
                                                                   }
                                                               } );
                                        }
                                        try
                                        {
                                            Thread.sleep( 50 );
                                        }
                                        catch( InterruptedException e )
                                        {
                                            break;
                                        }
                                    }
                                } );
        //th.setDaemon( true );
        th.start();
    }


    /*************************************************
     Project control related methods
     *************************************************/

    /*----------------------------------------
    Method name: set_first_sample_index
    Description: Set the value of the first sample index
                 so it stays within the boundaries.
    ----------------------------------------*/
    private void set_first_sample_index( int value )
    {
        if( first_sample_index != value )
        {
            displayed_interval_changed = true;
        }

        first_sample_index = value;
        if( first_sample_index >= sample_number - window_size )
        {
            first_sample_index = sample_number - window_size - 1;
        }
        if( first_sample_index < 0 )
        {
            first_sample_index = 0;
        }
    } /* set_first_sample_index */

    /*----------------------------------------
    Method name: on_data_source_modified()
    Description: Handle the change of the underlying data source. Make sure to lock the ProjectManager before calling this method
    ----------------------------------------*/
    private void on_data_source_changed() throws DataSourceException
    {
        if( sample_number != ProjectManager.getCache().get_sample_number() )
        {
            sample_number = ProjectManager.getCache().get_sample_number();

            current_sample_spinner.setValueFactory( new SpinnerValueFactory.IntegerSpinnerValueFactory( 0, sample_number, 0 ) );
            sel_len_spinner.setValueFactory( new SpinnerValueFactory.IntegerSpinnerValueFactory( 0, sample_number, 0 ) );

            time_scroll.setMin( 0 );
            time_scroll.setMax( sample_number );

            window_size = Math.min( window_size, sample_number );
            window_size_changed = true;
        }

        channel_number = ProjectManager.getCache().get_channel_number();
        sample_rate = ProjectManager.getCache().get_sample_rate();

        selection_changed = true;
        displayed_interval_changed = true;
        visible_samples_interval.l = 0;
        visible_samples_interval.r = 0;
    }

    private void start_effect_with_UI( Effect_UI_Component component, boolean create_new_project_version )
    {
        try
        {
            component.show( localScene.getWindow() );
            if( component.get_close_exception() != null )
            {
                throw component.get_close_exception();
            }
            else
            {
                apply_effect( component.get_prepared_effect(), false, create_new_project_version );
            }
        }
        catch( DataSourceException e )
        {
            treatException( e );
        }

    }

    private void apply_effect( IEffect effect, boolean allow_zero_selection, boolean create_new_project_version )
    {
        Interval interval = new Interval( selection.l, selection.r, false );
        if( effect == null || interval.get_length() < 0 )
        {
            return;
        }
        if( interval.get_length() == 0 )
        {
            if( allow_zero_selection )
            {
                return;
            }
            else
            {
                interval.l = 0;
                interval.r = sample_number;
            }
        }
        apply_effect( effect, interval, create_new_project_version );
    }

    private void apply_effect( IEffect effect, Interval interval, boolean create_new_project_version )
    {
        try
        {
            /*
            ProjectManager.lock_access();
            ProjectManager.apply_effect( effect, interval );
            */
            Effect_Progress_Bar_Dialog progress_bar_dialog = new Effect_Progress_Bar_Dialog( effect, interval, create_new_project_version );
            is_updater_suspended = true;
            progress_bar_dialog.show( localScene.getWindow() );
            if( progress_bar_dialog.get_close_exception() != null )
            {
                throw progress_bar_dialog.get_close_exception();
            }
            try
            {
                ProjectManager.lock_access();
                on_data_source_changed();
            }
            finally
            {
                ProjectManager.release_access();
            }
            displayed_interval_changed = true;
        }
        catch( DataSourceException | IOException e )
        {
            treatException( e );
        }

        is_updater_suspended = false;
    }

    /*----------------------------------------
    Method name: on_load_audio()
    Description: Handle loading of .wav file and initialize
                 GUI controls' actions
    ----------------------------------------*/

    private void on_load_audio( @Nullable ActionEvent ev )
    {
        /*--------------------------------
        Local Variables
        --------------------------------*/
        FileChooser fc = new FileChooser();

        /*--------------------------------
        Create "Open File" dialog
        --------------------------------*/
        fc.setSelectedExtensionFilter( new FileChooser.ExtensionFilter( "WAV Files", "*.wav" ) );
        File f = fc.showOpenDialog( localScene.getWindow() );
        String audio_file_path;
        if( f != null )
        {
            audio_file_path = f.getAbsolutePath();
        }
        else
        {
            /* cancel the initialization if the file open failed */
            return;
        }

        try
        {
            ProjectManager.lock_access();
            ProjectManager.new_project_from_audio_file( audio_file_path );

            /*--------------------------------
            Initialize variables and GUI components
            --------------------------------*/
            on_data_source_changed();
        }
        catch( Exception e )
        {
            treatException( e );
            //showDialog( Alert.AlertType.ERROR, "File load failed. " + e.getMessage() );
        }
        finally
        {
            ProjectManager.release_access();
        }
    } /* on_load_audio */

    private void on_export_project( boolean only_export_selection )
    {
        FileChooser fc = new FileChooser();

        fc.setSelectedExtensionFilter( new FileChooser.ExtensionFilter( "Audio files", "*.wav" ) );
        File f = fc.showSaveDialog( null );
        if( f != null )
        {
            try
            {
                Export_Progress_Bar_Dialog exporter = new Export_Progress_Bar_Dialog( f.getAbsolutePath(), only_export_selection && selection.get_length() > 0 ? selection : new Interval( 0, sample_number ) );
                is_updater_suspended = true;
                exporter.show( localScene.getWindow() );
                if( exporter.get_close_exception() != null )
                {
                    throw exporter.get_close_exception();
                }
                displayed_interval_changed = true;
            }
            catch( DataSourceException | IOException e )
            {
                treatException( e );
            }
            is_updater_suspended = false;
        }
    }

    private void on_load_marker( @Nullable ActionEvent ev )
    {
        FileChooser fc = new FileChooser();

        fc.setSelectedExtensionFilter( new FileChooser.ExtensionFilter( "Text files", "*.txt" ) );
        File f = fc.showOpenDialog( localScene.getWindow() );
        String path;
        if( f != null )
        {
            path = f.getAbsolutePath();
        }
        else
        {
            return;
        }
        try
        {
            ProjectManager.lock_access();
            ProjectManager.add_from_marker_file( path );
        }
        catch( FileNotFoundException | ParseException | DataSourceException e1 )
        {
            treatException( e1 );
        }
        finally
        {
            ProjectManager.release_access();
            markings_changed = true;
        }
    }

    private void on_export_marker( @Nullable ActionEvent ev )
    {
        FileChooser fc = new FileChooser();

        fc.setSelectedExtensionFilter( new FileChooser.ExtensionFilter( "Text files", "*.txt" ) );
        File f = fc.showSaveDialog( null );
        if( f != null )
        {
            try
            {
                ProjectManager.lock_access();
                ProjectManager.export_marker_file( f.getAbsolutePath() );
            }
            catch( IOException | DataSourceException e )
            {
                treatException( e );
            }
            finally
            {
                ProjectManager.release_access();
            }
        }
    }

    private void on_amplify( @Nullable ActionEvent ev )
    {
        start_effect_with_UI( new Amplify_Dialog(), true );
    }

    private void on_equalizer( @Nullable ActionEvent ev )
    {
        start_effect_with_UI( new Equalizer_Dialog( sample_rate ), true );
    }

    private void on_Repair( @Nullable ActionEvent ev )
    {
        start_effect_with_UI( new Repair_Marked_Dialog(), true );
    }

    private void on_derivation( @Nullable ActionEvent ev )
    {
        FIR_Filter effect = new FIR_Filter();
        effect.setFilter( FIR.derivation_FIR );
        apply_effect( effect, false, true );
    }

    private void on_integration( @Nullable ActionEvent ev )
    {
        IIR_Filter effect = new IIR_Filter();
        effect.setFilter( IIR.integration_IIR );
        apply_effect( effect, false, true );
    }

    private void on_add_marking( @Nullable ActionEvent ev )
    {
        if( selection.get_length() <= 0 )
        {
            return;
        }
        try
        {
            ProjectManager.lock_access();

            for( int c = 0; c < channel_number; c++ )
            {
                ProjectManager.add_marking( new Marking( selection.l, selection.r, c ) );
            }
        }
        catch( DataSourceException e )
        {
            treatException( e );
        }
        finally
        {
            ProjectManager.release_access();
            markings_changed = true;
        }
    }

    private void on_remove_marking( @Nullable ActionEvent ev )
    {
        if( selection.get_length() <= 0 )
        {
            return;
        }
        try
        {
            ProjectManager.lock_access();

            for( int c = 0; c < channel_number; c++ )
            {
                ProjectManager.remove_marking( new Marking( selection.l, selection.r, c ) );
            }
        }
        catch( DataSourceException e )
        {
            treatException( e );
        }
        finally
        {
            ProjectManager.release_access();
            markings_changed = true;
        }
    }

    private void on_undo( @Nullable ActionEvent ev )
    {
        try
        {
            ProjectManager.lock_access();
            ProjectManager.undo();
            on_data_source_changed();
        }
        catch( DataSourceException e )
        {
            treatException( e );
        }
        finally
        {
            ProjectManager.release_access();
        }
    }

    private void on_redo( @Nullable ActionEvent ev )
    {
        try
        {
            ProjectManager.lock_access();
            ProjectManager.redo();
            on_data_source_changed();
        }
        catch( DataSourceException e )
        {
            treatException( e );
        }
        finally
        {
            ProjectManager.release_access();
        }
    }

    private void on_generate_markings( @Nullable ActionEvent ev )
    {
        start_effect_with_UI( new Generate_Markings_Dialog(), false );
        markings_changed = true;
    }

    private void on_clear_markings( @Nullable ActionEvent ev )
    {
        try
        {
            ProjectManager.lock_access();
            ProjectManager.clear_all_markings();
            markings_changed = true;
        }
        catch( DataSourceException e )
        {
            treatException( e );
        }
        finally
        {
            ProjectManager.release_access();
        }
    }

    private void on_groove_retracking( ActionEvent actionEvent )
    {
        Groove_Retracking eff = new Groove_Retracking();
        eff.setStylus_length( 1 );
        eff.setStylus_width( 1 );
        apply_effect( eff, false, true );
    }

    private void on_generate_dataset( ActionEvent ev )
    {
        FileChooser fc = new FileChooser();

        fc.setSelectedExtensionFilter( new FileChooser.ExtensionFilter( "Text files", "*.txt" ) );
        File f = fc.showSaveDialog( null );
        if( f != null )
        {
            try
            {
                is_updater_suspended = true;
                ProjectManager.lock_access();
                Interval interval = new Interval( selection.l, selection.r, false );
                if( interval.get_length() <= 0 )
                {
                    return;
                }
                DataSetGenerator.generate( ProjectManager.getCache(), interval, f.getAbsolutePath(), 48, 0.01 );
            }
            catch( DataSourceException | IOException e )
            {
                treatException( e );
            }
            finally
            {
                ProjectManager.release_access();
            }
            is_updater_suspended = false;
        }
    }

    private void on_close_application()
    {
        run_updater = false;
        try
        {
            Thread.sleep( 100 );
            ProjectManager.lock_access();
            ProjectManager.discard_project();
        }
        catch( DataSourceException | InterruptedException e1 )
        {
            treatException( e1 );
        }
        finally
        {
            ProjectManager.release_access();
        }
        //TBD: Show save/don't save dialog
    }
}