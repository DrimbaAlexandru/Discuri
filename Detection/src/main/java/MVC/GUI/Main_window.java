package MVC.GUI;

import AudioDataSource.ADCache.AudioSamplesWindow;
import AudioDataSource.ADCache.CachedAudioDataSource;
import AudioDataSource.Exceptions.DataSourceException;
import AudioDataSource.FileADS.WAVFileAudioSource;
import AudioDataSource.IAudioDataSource;
import AudioDataSource.Utils;
import ProjectStatics.ProjectStatics;
import SignalProcessing.Effects.*;
import SignalProcessing.LiniarPrediction.BurgLP;
import SignalProcessing.LiniarPrediction.LinearPrediction;
import Utils.Interval;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.text.ParseException;
import java.util.Arrays;

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
    private MenuItem menu_open_file, menu_export, menu_load_marker, menu_save_marker;
    @FXML
    private Menu menu_effects;

    /*--------------------------------
    Position, selection and audio data variables
    --------------------------------*/
    private int zoom_index = 0;
    private int window_size = 32768;
    private int first_sample_index = 0;

    private boolean update_samples_window = true;

    private int selection_start_index = 0;
    private int selection_started_index = 0;
    private int selection_end_index = 0;

    private double l_window[] = new double[ 0 ];
    private double r_window[] = new double[ 0 ];

    private String raw_wav_filepath = "";

    private CachedAudioDataSource dataSource = null;
    private int sample_number;
    private int channel_number;

    /*--------------------------------
    GUI related variables
    --------------------------------*/
    private int window_left_pad = 25;
    private int display_window_height;
    private int display_window_width;

    private boolean run_updater = true;
    private boolean main_canvas_LMB_down_on_samples;
    private boolean main_canvas_LMB_down_on_zoom;
    private int periodic_window_increment = 0;

    private boolean inv_select, inv_samples; /* Flags for updating selection and samples related GUI components


    /*************************************************
    Private Methods
    *************************************************/

    private void showDialog( Alert.AlertType type, String message )
    {
        Alert alert = new Alert( type );
        alert.setTitle( type.name() );
        alert.setHeaderText( null );
        alert.setContentText( message );
        alert.showAndWait();
    }

    /*----------------------------------------
    Method name: set_first_sample_index
    Description: Set the value of the first sample index
                 so it stays within the boundaries.
    ----------------------------------------*/

    private void set_first_sample_index
    (
        int value       /* new value for the first sample index */
    )
    {
        if( first_sample_index != value )
        {
            update_samples_window = true;
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
        inv_samples = true;
    } /* set_first_sample_index */


    /*----------------------------------------
    Method name: refresh_view()
    Description: Refresh the GUI to match the current
                 position, selection etc.
    ----------------------------------------*/

    private void refresh_view()
    {
        if( inv_select || inv_samples )
        {
            drawSamples();
            refreshSelection();
        }
    } /* refresh_view */


    /*----------------------------------------
    Method name: loadWAV()
    Description: Handle loading of .wav file and initialize
                 GUI controls' actions
    ----------------------------------------*/

    private void loadWAV()
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
        if( f != null )
        {
            raw_wav_filepath = f.getAbsolutePath();
        }
        else
        {
            raw_wav_filepath = ""; /* cancel the initialization if the file open failed */
            return;
        }

        try
        {
            ProjectStatics.loadAudioFile( raw_wav_filepath );
            dataSource = new CachedAudioDataSource( ProjectStatics.getVersionedADS().get_current_version(), 44100, 512 );
            window_size = Math.min( window_size, dataSource.get_sample_number() );
        }
        catch( Exception e )
        {
            treatException( e );
            showDialog( Alert.AlertType.ERROR, "File load failed. " + e.getMessage() );
            return;
        }

        /*--------------------------------
        Initialize variables and GUI components
        --------------------------------*/
        inv_select = true;
        inv_samples = true;
        update_samples_window = true;

        sample_number = dataSource.get_sample_number();
        channel_number = dataSource.get_channel_number();

        current_sample_spinner.setValueFactory( new SpinnerValueFactory.IntegerSpinnerValueFactory( 0, sample_number, 0 ) );
        sel_len_spinner.setValueFactory( new SpinnerValueFactory.IntegerSpinnerValueFactory( 0, sample_number, 0 ) );

        time_scroll.setMin( 0 );
        time_scroll.setMax( sample_number );
        time_scroll.setUnitIncrement( 1 );
        time_scroll.setOnMouseClicked( e ->
                                       {
                                           set_first_sample_index( ( int )time_scroll.getValue() );
                                       } );
        time_scroll.setOnDragDone( e ->
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
                                                    update_samples_window = true;
                                                    window_size /= 2;
                                                    if( window_size < 4 )
                                                    {
                                                        window_size = 4;
                                                    }
                                                    set_first_sample_index( ( selection_start_index + selection_end_index ) / 2 - window_size / 2 );
                                                    inv_samples = true;
                                                } );
        btn_expand_select.setOnMouseClicked( e ->
                                             {
                                                 update_samples_window = true;
                                                 window_size *= 2;
                                                 if( window_size > sample_number )
                                                 {
                                                     window_size = sample_number;
                                                 }
                                                 set_first_sample_index( first_sample_index - window_size / 4 );
                                                 inv_samples = true;
                                             } );
        btn_zoom_in.setOnMouseClicked( e->
                                       {
                                           zoom_index++;
                                           inv_samples = true;
                                       });
        btn_zoom_out.setOnMouseClicked( e->
                                        {
                                            zoom_index--;
                                            inv_samples = true;
                                        });
        main_canvas.setOnMousePressed( e->
                                       {
                                           System.out.println( "Pressed" );
                                           main_canvas_LMB_down_on_samples = false;
                                           main_canvas_LMB_down_on_zoom = false;
                                           if( e.getX() >= window_left_pad && e.getX() <=main_canvas.getWidth() ) /* Selection change handling */
                                           {
                                               selection_start_index = ( int )( first_sample_index + window_size * ( ( e.getX() - window_left_pad ) / ( display_window_width ) ) );
                                               selection_started_index = selection_start_index;
                                               selection_end_index = selection_start_index;
                                               inv_select = true;
                                               main_canvas_LMB_down_on_samples = true;
                                           }
                                           if( e.getX() < window_left_pad && e.getX() >= 0 ) /* Zoom change handling */
                                           {
                                               main_canvas_LMB_down_on_zoom = true;
                                           }

                                       });
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
                                               inv_select = true;
                                               int temp = ( int )( first_sample_index + window_size * ( ( e.getX() - window_left_pad ) / ( display_window_width ) ) );

                                               if( temp < selection_started_index )
                                               {
                                                   selection_start_index = temp;
                                                   selection_end_index = selection_started_index;
                                               }
                                               else
                                               {
                                                   selection_start_index = selection_started_index;
                                                   selection_end_index = temp;
                                               }
                                               if( e.getX() < window_left_pad || e.getX() > main_canvas.getWidth() )
                                               {
                                                   periodic_window_increment = ( int )( window_size * ( ( e.getX() - window_left_pad ) / ( display_window_width ) ) );
                                               }
                                               if( e.getX() > main_canvas.getWidth() )
                                               {
                                                   periodic_window_increment = ( int )( window_size * ( ( e.getX() - main_canvas.getWidth() ) / ( display_window_width ) ) );
                                               }
                                           }
                                           else /* Vertical zoom handling */
                                           {
                                               ;
                                           }

                                       } );
        localScene.setOnKeyReleased( e ->
                                     {
                                         if( e.getCode() == KeyCode.I && e.isControlDown() )
                                         {
                                             onApplyRepairSelected( new Repair() );
                                         }
                                         if( e.getCode() == KeyCode.M && e.isControlDown() )
                                         {
                                             onApplyMarkSelected( new Mark_selected() );
                                         }
                                     } );

    } /* loadWAV */


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

        /* Handle no WAV file loaded case */
        if( dataSource == null )
        {
            return;
        }

        /*--------------------------------
        Initialize variables
        --------------------------------*/
        if( window_size < display_window_width )
        {
            display_window_size = window_size;
        }
        else
        {
            display_window_size = 2 * display_window_width;
        }

        if( display_window_size > l_window.length )
        {
            l_window = new double[ display_window_size ];
            r_window = new double[ display_window_size ];
        }
        /*--------------------------------
        Redraw the canvas
        --------------------------------*/
        gc.setFill( Color.WHITE );
        gc.fillRect( 0, 0, main_canvas.getWidth(), main_canvas.getHeight() );

        gc.setFill( new Color( 0, 0.5, 1, 0.1 ) );
        gc.fillRect( window_left_pad + ( double )( selection_start_index - first_sample_index ) / window_size * display_window_width, 0, ( double )( selection_end_index - selection_start_index ) / window_size * display_window_width + 1, main_canvas.getHeight() );

        gc.setStroke( Color.BLACK );
        gc.strokeLine( window_left_pad, 0, window_left_pad, main_canvas.getHeight() );
        gc.strokeLine( window_left_pad, main_canvas.getHeight() / 2, main_canvas.getWidth(), main_canvas.getHeight() / 2 );
        gc.strokeRect( 0, 0, main_canvas.getWidth(), main_canvas.getHeight() );

        //System.out.println( "Window size: " + window_size );
        //System.out.println( "Window from sample " + first_sample_index + " to sample " + last_sample_index );

        try
        {
            if( update_samples_window )
            {
                win = dataSource.get_resized_samples( first_sample_index, window_size, display_window_size );
                update_samples_window = false;
                for( i = 0; i < display_window_size; i++ )
                {
                    l_window[ i ] = win.getSample( i, 0 );
                    r_window[ i ] = win.getSample( i, ( channel_number < 2 ) ? 0 : 1 );
                }
            }
        }
        catch( Exception e )
        {
            treatException( e );
            return;
        }

        for( i = 0; i < display_window_size - 1; i++ )
        {
            /* Draw L channel */
            if( ProjectStatics.getMarkerFile() != null && ProjectStatics.getMarkerFile().isMarked( first_sample_index + i, 0 ) )
            {
                gc.setStroke( Color.RED );
            }
            else
            {
                gc.setStroke( Color.GREEN );
            }
            gc.strokeLine( i * display_window_width / ( display_window_size ) + window_left_pad,
                           ( 1 + Math.min( Math.max( -l_window[ i ] * Math.pow( 2, zoom_index ), -1 ), 1 ) ) * display_window_height / 2,
                           ( i + 1 ) * display_window_width / ( display_window_size ) + window_left_pad,
                           ( 1 + Math.min( Math.max( -l_window[ i + 1 ] * Math.pow( 2, zoom_index ), -1 ), 1 ) ) * display_window_height / 2 );

            /* Draw R channel */
            if( ProjectStatics.getMarkerFile() != null && ProjectStatics.getMarkerFile().isMarked( first_sample_index + i, 1 ) )
            {
                gc.setStroke( Color.RED );
            }
            else
            {
                gc.setStroke( Color.BLUE );
            };
            gc.strokeLine( i * display_window_width / ( display_window_size ) + window_left_pad,
                           ( 1 + Math.min( Math.max( -r_window[ i ] * Math.pow( 2, zoom_index ), -1 ), 1 ) ) * display_window_height / 2,
                           ( i + 1 ) * display_window_width / ( display_window_size )+ window_left_pad,
                           ( 1 + Math.min( Math.max( -r_window[ i + 1 ] * Math.pow( 2, zoom_index ), -1 ), 1 ) ) * display_window_height / 2 );

            /* Draw L-R channel */
            gc.setStroke( Color.PINK );
            gc.strokeLine( i * display_window_width / ( display_window_size ) + window_left_pad,
                           ( 1 + Math.min( Math.max( -( l_window[ i ] - r_window[ i ] ) * Math.pow( 2, zoom_index - 1 ), -1 ), 1 ) ) * display_window_height / 2,
                           ( i + 1 ) * display_window_width / ( display_window_size ) + window_left_pad,
                           ( 1 + Math.min( Math.max( -( l_window[ i + 1] - r_window[ i + 1 ] ) * Math.pow( 2, zoom_index - 1 ), -1 ), 1 ) ) * display_window_height / 2 );
        }
        main_canvas.getGraphicsContext2D().setFill( Color.BLACK );
        inv_samples = false;
    } /* drawSamples */


    /*----------------------------------------
    Method name: refreshSelection
    Description: Update selection related UI controls
    ----------------------------------------*/

    private void refreshSelection()
    {
        if( dataSource == null )
        {
            return;
        }
        final int sel_len = selection_end_index - selection_start_index;
        final int seconds = selection_start_index / dataSource.get_sample_rate();
        final int milliseconds = ( selection_start_index % dataSource.get_sample_rate() * 1000 ) / dataSource.get_sample_rate();
        time_scroll.setValue( selection_start_index );
        position_indicator.setText( "samples / " + ( sample_number ) +
                                            " ( " + String.format( "%02d", seconds / 360 ) +
                                            ":" + String.format( "%02d", seconds / 60 % 60 ) +
                                            ":" + String.format( "%02d", seconds % 60 ) +
                                            "." + String.format( "%03d", milliseconds ) +
                                            " / " + String.format( "%02d", ( sample_number / dataSource.get_sample_rate() ) / 3600 ) +
                                            ":" + String.format( "%02d", sample_number / dataSource.get_sample_rate() / 60 % 60 ) +
                                            ":" + String.format( "%02d", sample_number / dataSource.get_sample_rate() % 60 ) +
                                            "." + String.format( "%02d", ( sample_number % dataSource.get_sample_rate() * 1000 ) / dataSource.get_sample_rate() ) + " )" );
        current_sample_spinner.getValueFactory().setValue( selection_start_index );
        sel_len_indicator.setText( "samples ( "+
                                           String.format( "%02d", sel_len / dataSource.get_sample_rate() / 3600 ) +
                                           ":" + String.format( "%02d", sel_len / dataSource.get_sample_rate() / 60 % 60 ) +
                                           ":" + String.format( "%02d", sel_len / dataSource.get_sample_rate() % 60 ) +
                                           "." + String.format( "%02d", ( sel_len % dataSource.get_sample_rate() * 1000 ) / dataSource.get_sample_rate() ) + " )" );
        sel_len_spinner.getValueFactory().setValue( sel_len );
        time_scroll.setBlockIncrement( window_size );
        time_scroll.setValue( first_sample_index );
        inv_select = false;
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
        l.setLocation( getClass().getClassLoader().getResource( "fxml_main.fxml" ) );
        ProjectStatics.registerEffect( new Mark_selected() );
        ProjectStatics.registerEffect( new Repair() );
        ProjectStatics.registerEffect( new Repair_Marked() );
        ProjectStatics.registerEffect( new Sample_Summer() );
        ProjectStatics.registerEffect( new Repair_in_high_pass() );

        try
        {
            mainLayout = l.load();
            //markerFile = MarkerFile.fromFile( "C:\\Users\\Alex\\Desktop\\marker.txt" );
            //markerFile = new MarkerFile( "C:\\Users\\Alex\\Desktop\\marker.txt" );
            display_window_height = ( int )main_canvas.getHeight();
            display_window_width = ( int )main_canvas.getWidth() - window_left_pad;
            startRefresher();

            menu_open_file.setOnAction( e ->
                                        {
                                            loadWAV();
                                        } );
            menu_load_marker.setOnAction( e->
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
                                                  ProjectStatics.loadMarkerFile( path );
                                              }
                                              catch( FileNotFoundException | ParseException e1 )
                                              {
                                                  showDialog( Alert.AlertType.ERROR, e1.getMessage() );
                                              }
                                          });

            menu_save_marker.setOnAction( ev->
                                          {
                                              FileChooser fc = new FileChooser();

                                              fc.setSelectedExtensionFilter( new FileChooser.ExtensionFilter( "Text files", "*.txt" ) );
                                              File f = fc.showSaveDialog( null );
                                              if( f != null )
                                              {
                                                  try
                                                  {
                                                      ProjectStatics.getMarkerFile().writeMarkingsToFile( new FileWriter( f ) );
                                                  }
                                                  catch( IOException e )
                                                  {
                                                      treatException( e );
                                                  }
                                              }
                                          });
            menu_export.setOnAction( ev->{
                FileChooser fc = new FileChooser();

                fc.setSelectedExtensionFilter( new FileChooser.ExtensionFilter( "Text files", "*.txt" ) );
                File f = fc.showSaveDialog( null );
                if( f != null )
                {
                    try
                    {
                        if( ProjectStatics.getVersionedADS() != null )
                        {
                            Utils.copyToADS( dataSource, new WAVFileAudioSource( f.getAbsolutePath(), dataSource.get_channel_number(), dataSource.get_sample_rate(), 2 ) );
                        }
                    }
                    catch( DataSourceException e )
                    {
                        treatException( e );
                    }
                }
            } );

            btn_redo.setOnAction( ev ->
                                  {
                                      ProjectStatics.getVersionedADS().redo();
                                      onDataSourceChanged();
                                  } );
            btn_undo.setOnAction( ev ->
                                  {
                                      ProjectStatics.getVersionedADS().undo();
                                      onDataSourceChanged();
                                  } );

            for( IEffect eff : ProjectStatics.getEffectList() )
            {
                MenuItem mi = new MenuItem( eff.getName() );
                menu_effects.getItems().add( mi );
                if( eff.getClass().getCanonicalName().equals( Mark_selected.class.getCanonicalName() ) )
                {
                    final Mark_selected effect = ( Mark_selected )eff;
                    mi.setOnAction( ev ->
                                    {
                                        System.out.println( effect.getName() );
                                        onApplyMarkSelected( effect );
                                    } );
                    continue;
                }
                if( eff.getClass().getCanonicalName().equals( Repair.class.getCanonicalName() ) )
                {
                    final Repair effect = ( Repair )eff;
                    mi.setOnAction( ev ->
                                    {
                                        System.out.println( effect.getName() );
                                        onApplyRepairSelected( effect );
                                    } );
                    continue;
                }
                if( eff.getClass().getCanonicalName().equals( Repair_Marked.class.getCanonicalName() ) )
                {
                    final Repair_Marked effect = ( Repair_Marked )eff;
                    mi.setOnAction( ev ->
                                    {
                                        System.out.println( effect.getName() );
                                        onApplyRepairMarked( effect );
                                    } );
                    continue;
                }
                if( eff.getClass().getCanonicalName().equals( Sample_Summer.class.getCanonicalName() ) )
                {
                    final Sample_Summer effect = ( Sample_Summer )eff;
                    mi.setOnAction( ev ->
                                    {
                                        System.out.println( effect.getName() );
                                        onApplySampleSummer( effect );
                                    } );
                    continue;
                }
                if( eff.getClass().getCanonicalName().equals( Repair_in_high_pass.class.getCanonicalName() ) )
                {
                    final Repair_in_high_pass effect = ( Repair_in_high_pass )eff;
                    mi.setOnAction( ev ->
                                    {
                                        System.out.println( effect.getName() );
                                        onApplyRepair_in_high_pass( effect );
                                    } );
                    continue;
                }

            }
        }
        catch( IOException e )
        {
            treatException( e );
        }
    } /* Main_window */

    private void onApplySampleSummer( Sample_Summer eff )
    {
        apply_effect( eff, false, true );
    }

    private void onApplyRepair_in_high_pass( Repair_in_high_pass eff )
    {
        apply_effect( eff, false, false );
    }

    private void onApplyRepairMarked( Repair_Marked eff )
    {
        eff.setMin_fetch_ratio( 32 );
        eff.setMin_fetch_size( 512 );
        apply_effect( eff, false, false );
    }

    private void onApplyRepairSelected( Repair eff )
    {
        eff.setAffected_channels( Arrays.asList( 0, 1 ) );
        eff.set_fetch_ratio( Math.max( 512 / ( selection_end_index - selection_start_index ), 32 ) );
        apply_effect( eff, false, true );
    }

    private void onApplyMarkSelected( Mark_selected eff )
    {
        eff.setAffected_channels( Arrays.asList( 0, 1 ) );
        apply_effect( eff, false, true );
    }

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

        Stage localStage = new Stage();
        Group localroot = new Group();

        localScene = new Scene( localroot, 834, 474, Color.color( 1, 1, 1 ) );
        localStage.setResizable( false );
        localStage.setScene( localScene );
        localroot.getChildren().add( mainLayout );
        localStage.show();
        refresh_view();
        localStage.setOnCloseRequest( ( e ) ->
                                      {
                                          run_updater = false;
                                          try
                                          {
                                              Thread.sleep( 100 );
                                              if( ProjectStatics.getVersionedADS() != null )
                                              {
                                                  ProjectStatics.getVersionedADS().dispose();
                                              }
                                          }
                                          catch( InterruptedException e1 )
                                          {
                                              treatException( e1 );
                                          }
                                          //TBD: Show save/don't save dialog
                                      } );
    } /* run() */


    private void startRefresher()
    {
        Thread th = new Thread( () ->
                                {
                                    while( run_updater )
                                    {
                                        Platform.runLater( () ->
                                                           {
                                                               if( periodic_window_increment != 0 )
                                                               {
                                                                   if( periodic_window_increment > 0 )
                                                                   {
                                                                       periodic_window_increment = Math.min( periodic_window_increment, window_size / 2 );
                                                                       set_first_sample_index( first_sample_index + periodic_window_increment );
                                                                       selection_end_index = first_sample_index + window_size;
                                                                   }
                                                                   else
                                                                   {
                                                                       periodic_window_increment = Math.max( periodic_window_increment, -window_size / 2 );
                                                                       set_first_sample_index( first_sample_index + periodic_window_increment );
                                                                       selection_start_index = first_sample_index;
                                                                   }

                                                               }
                                                               refresh_view();
                                                           } );
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

    /*
    private void interpolate_selection()
    {
        int ch, i, len = selection_end_index - selection_start_index;
        int side_len = Math.min( Math.max( 1024, len ), Math.min( selection_start_index, sample_number - selection_end_index ) );
        double[] left = new double[ side_len ];
        double[] center = new double[ len ];
        double[] right = new double[ side_len  ];
        AudioSamplesWindow win;
        for( ch = 0; ch < dataSource.get_channel_number(); ch++ )
        {
            try
            {
                win = dataSource.get_samples( selection_start_index - side_len, side_len );
                for( i = 0; i < side_len ; i++ )
                {
                    left[ i ] = win.getSample( selection_start_index - side_len + i, ch );
                }
                win = dataSource.get_samples( selection_start_index + len, side_len );
                for( i = 0; i < side_len; i++ )
                {
                    right[ i ] = win.getSample( selection_start_index + len + i, ch );
                }

                LinearPrediction LP = new BurgLP();

                try
                {
                    LP.extrapolate( left, center, right, side_len, len, side_len );
                    if( ch == 0 )
                    {
                        for( i = 0; i < len; i++ )
                        {
                            l_window[ selection_start_index - first_sample_index + i ] = center[ i ];
                        }
                    }
                    else
                    {
                        for( i = 0; i < len; i++ )
                        {
                            r_window[ selection_start_index - first_sample_index + i ] = center[ i ];
                        }
                    }
                }
                catch( DataSourceException e )
                {
                    treatException( e );
                }

                inv_samples = true;
            }
            catch( DataSourceException e )
            {
                treatException( e );
            }
        }
    }
    */

    private void treatException( Exception e )
    {
        e.printStackTrace();
    }

    private void apply_effect( IEffect effect, boolean allow_zero_selection, boolean use_destination_as_source )
    {
        Interval interval = new Interval( selection_start_index, selection_end_index - selection_start_index );
        if( interval.get_length() < 0 )
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
                interval.r = dataSource.get_sample_number();
            }
        }
        try
        {
            dataSource.flushAll();
            IAudioDataSource srcDS = null;
            if( !use_destination_as_source )
            {
                srcDS = new CachedAudioDataSource( ProjectStatics.getVersionedADS().get_current_version(),
                                                   ProjectStatics.getDefault_cache_size(),
                                                   ProjectStatics.getDefault_cache_page_size() );
            }
            dataSource.setDataSource( ProjectStatics.getVersionedADS().create_new() );
            if( use_destination_as_source )
            {
                srcDS = dataSource;
            }
            effect.apply( srcDS, dataSource, interval );
            inv_samples = true;
            update_samples_window = true;
        }
        catch( DataSourceException e )
        {
            treatException( e );
        }
    }


    private void onDataSourceChanged()
    {
        try
        {
            inv_samples = true;
            update_samples_window = true;
            dataSource.setDataSource( ProjectStatics.getVersionedADS().get_current_version() );
        }
        catch( DataSourceException e )
        {
            treatException( e );
        }
    }

}

/* TODO:
* - Make get_compact_samples() use more values when computing min/max values; optimize to minimize reads.
* - Implement get_compact_samples() branch for when sample_number is larger than the cache size.
* - Add controls for: zoom factor, window size;
 */