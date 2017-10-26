package MVC.GUI;

import WavFile.BasicWavFile.BasicWavFileException;
import WavFile.WavCache.WavCachedWindow;
import WavFile.WavFile;
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

import java.io.File;
import java.io.IOException;
import java.util.Observable;
import java.util.Observer;

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
    private Button btn_open_wav, btn_load_mark, btn_save_mark;
    @FXML
    private Button btn_prev_frame, btn_prev_sample, btn_next_frame, btn_next_sample;
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

    /*--------------------------------
    Position, selection and audio data variables
    --------------------------------*/
    private int zoom_index = 0;
    private int window_size = 16384;
    private int first_sample_index = 0;

    private int selection_start_index = 0;
    private int selection_end_index = 0;

    private double l_window[];
    private double r_window[];

    private String raw_wav_filepath = "";

    private WavFile wavFile = null;
    private int sample_number;
    private int channel_number;

    /*--------------------------------
    GUI related variables
    --------------------------------*/
    private int window_left_pad = 25;
    private int display_window_height;
    private int display_window_width;

    private boolean inv_select, inv_samples; /* Flags for updating selection and samples related GUI components


    /*************************************************
    Private Methods
    *************************************************/
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
        if( inv_samples )
        {
            drawSamples();
        }

        if( inv_select )
        {
            refreshSelection();
        }
    } /* refresh_view */


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
        try
        {
            mainLayout = l.load();
            display_window_height = ( int )main_canvas.getHeight();
            display_window_width = ( int )main_canvas.getWidth() - window_left_pad;
            btn_open_wav.setOnMouseClicked( e ->
                                            {
                                                loadWAV();
                                            } );
        }
        catch( IOException e )
        {
            e.printStackTrace();
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
                                          //TBD: Show save/don't save dialog
                                      } );
    } /* run() */


    /*----------------------------------------
    Method name: loadWAV()
    Description: Handle loading of .wav file
    ----------------------------------------*/

    private void loadWAV()
    {
        FileChooser fc = new FileChooser();

        fc.setSelectedExtensionFilter( new FileChooser.ExtensionFilter( "WAV Files", "*.wav" ) );
        File f = fc.showOpenDialog( localScene.getWindow() );
        if( f != null )
        {
            raw_wav_filepath = f.getAbsolutePath();
        }
        else
        {
            raw_wav_filepath = "";
            return;
        }

        try
        {
            wavFile = new WavFile( raw_wav_filepath, 44100 );
        }
        catch( Exception e )
        {
            e.printStackTrace();
            return;
        }

        inv_select = true;
        inv_samples = true;

        sample_number = ( int )wavFile.getSampleNumber();
        channel_number = wavFile.getChannelsNumber();

        time_scroll.setMin( 0 );
        time_scroll.setMax( sample_number );
        time_scroll.setBlockIncrement( window_size );
        time_scroll.setUnitIncrement( 1 );
        time_scroll.setOnMouseClicked( e->{
            set_first_sample_index( ( int )time_scroll.getValue() );
            refresh_view();} );

        current_sample_spinner.setValueFactory( new SpinnerValueFactory.IntegerSpinnerValueFactory( 0, sample_number, 0 ) );
        sel_len_spinner.setValueFactory( new SpinnerValueFactory.IntegerSpinnerValueFactory( 0, sample_number, 0 ) );

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
                                                    inv_samples = true;
                                                    refresh_view();
                                                } );
        btn_expand_select.setOnMouseClicked( e ->
                                             {
                                                 window_size *= 2;
                                                 if( window_size > sample_number )
                                                 {
                                                     window_size = sample_number;
                                                 }
                                                 inv_samples = true;
                                                 refresh_view();
                                             } );
        btn_zoom_in.setOnMouseClicked( e->
                                       {
                                           zoom_index++;
                                           inv_samples = true;
                                           refresh_view();
                                       });
        btn_zoom_out.setOnMouseClicked( e->
                                        {
                                            zoom_index--;
                                            inv_samples = true;
                                            refresh_view();
                                        });
        main_canvas.setOnMousePressed( e->
                                       {
                                           if( e.getX() >= window_left_pad ) // Selection change handling
                                           {
                                               selection_start_index = ( int )( first_sample_index + window_size * ( ( e.getX() - window_left_pad ) / ( display_window_width ) ) );
                                               inv_select = true;
                                           }
                                           else //Vertical zoom handling
                                           {

                                           }

                                           System.out.println( "Pressed" );
                                       });
        main_canvas.setOnMouseMoved( e ->
                                        {
                                            inv_select = true;

                                            int temp = selection_start_index;
                                            selection_end_index = ( int )( first_sample_index + window_size * ( ( e.getX() - window_left_pad ) / ( display_window_width ) ) );
                                            selection_start_index = Math.min( temp, selection_end_index );
                                            selection_end_index = Math.max( temp, selection_end_index );

                                            System.out.println( "Released" );
                                            System.out.println( selection_start_index );
                                            System.out.println( selection_end_index );
                                            refresh_view();
                                        } );

        refresh_view();
    }

    private void drawSamples()
    {
        int i;  /* loop index */
        int last_sample_index;
        int display_window_size;
        WavCachedWindow win;
        final GraphicsContext gc = main_canvas.getGraphicsContext2D();

        if( window_size < display_window_width )
        {
            display_window_size = window_size;
        }
        else
        {
            display_window_size = 2 * display_window_width;
        }
        l_window = new double[ display_window_size ];
        r_window = new double[ display_window_size ];

        gc.setFill( Color.WHITE );
        gc.fillRect( 0, 0, main_canvas.getWidth(), main_canvas.getHeight() );
        gc.setStroke( Color.BLACK );
        gc.strokeLine( window_left_pad, 0, window_left_pad, main_canvas.getHeight() );
        gc.strokeLine( window_left_pad, main_canvas.getHeight() / 2, main_canvas.getWidth(), main_canvas.getHeight() / 2 );
        gc.strokeRect( 0, 0, main_canvas.getWidth(), main_canvas.getHeight() );

        if( wavFile == null )
        {
            return;
        }

        last_sample_index = first_sample_index + window_size - 1;

        System.out.println( "Window size: " + window_size );
        System.out.println( "Window from sample " + first_sample_index + " to sample " + last_sample_index );

        try
        {
            win = wavFile.get_compact_samples( first_sample_index, window_size, display_window_size, false );
            for( i = 0; i < display_window_size; i++ )
            {
                l_window[ i ] = win.getSample( i, 0 );
                r_window[ i ] = win.getSample( i, ( channel_number < 2 ) ? 0 : 1 );
            }
        }
        catch( IOException | BasicWavFileException e )
        {
            e.printStackTrace();
            return;
        }

        for( i = 0; i < display_window_size - 1; i++ )
        {
            gc.setStroke( Color.GREEN );
            gc.strokeLine( i * display_window_width / ( display_window_size ) + window_left_pad,
                           ( 1 + Math.min( Math.max( -l_window[ i ] * Math.pow( 2, zoom_index - 1 ), -1 ), 1 ) ) * display_window_height / 2,
                           ( i + 1 ) * display_window_width / ( display_window_size ) + window_left_pad,
                           ( 1 + Math.min( Math.max( -l_window[ i + 1 ] * Math.pow( 2, zoom_index - 1 ), -1 ), 1 ) ) * display_window_height / 2 );

            gc.setStroke( Color.BLUE );
            gc.strokeLine( i * display_window_width / ( display_window_size ) + window_left_pad,
                           ( 1 + Math.min( Math.max( -r_window[ i ] * Math.pow( 2, zoom_index - 1 ), -1 ), 1 ) ) * display_window_height / 2,
                           ( i + 1 ) * display_window_width / ( display_window_size )+ window_left_pad,
                           ( 1 + Math.min( Math.max( -r_window[ i + 1 ] * Math.pow( 2, zoom_index - 1 ), -1 ), 1 ) ) * display_window_height / 2 );

            gc.setStroke( Color.PINK );
            gc.strokeLine( i * display_window_width / ( display_window_size ) + window_left_pad,
                           ( 1 + Math.min( Math.max( -( l_window[ i ] - r_window[ i ] ) * Math.pow( 2, zoom_index - 1 ), -1 ), 1 ) ) * display_window_height / 2,
                           ( i + 1 ) * display_window_width / ( display_window_size ) + window_left_pad,
                           ( 1 + Math.min( Math.max( -( l_window[ i + 1] - r_window[ i + 1 ] ) * Math.pow( 2, zoom_index - 1 ), -1 ), 1 ) ) * display_window_height / 2 );
        }
        main_canvas.getGraphicsContext2D().setFill( Color.BLACK );
        inv_samples = false;
    }


    private void refreshSelection()
    {
        if( wavFile == null )
        {
            return;
        }
        final int sel_len = selection_end_index - selection_start_index;
        final int seconds = selection_start_index / wavFile.getSampleRate();
        final int milliseconds = ( selection_start_index % wavFile.getSampleRate() * 1000 ) / wavFile.getSampleRate();
        time_scroll.setValue( selection_start_index );
        position_indicator.setText( "samples / " + ( sample_number ) +
                                            " ( " + String.format( "%02d", seconds / 360 ) +
                                            ":" + String.format( "%02d", seconds / 60 % 60 ) +
                                            ":" + String.format( "%02d", seconds % 60 ) +
                                            "." + String.format( "%03d", milliseconds ) +
                                            " / " + String.format( "%02d", ( sample_number / wavFile.getSampleRate() ) / 3600 ) +
                                            ":" + String.format( "%02d", sample_number / wavFile.getSampleRate() / 60 % 60 ) +
                                            ":" + String.format( "%02d", sample_number / wavFile.getSampleRate() % 60 ) +
                                            "." + String.format( "%02d", ( sample_number % wavFile.getSampleRate() * 1000 ) / wavFile.getSampleRate() ) + " )" );
        current_sample_spinner.getValueFactory().setValue( selection_start_index );
        sel_len_indicator.setText( "samples ( "+
                                           String.format( "%02d", sel_len / wavFile.getSampleRate() / 3600 ) +
                                           ":" + String.format( "%02d", sel_len / wavFile.getSampleRate() / 60 % 60 ) +
                                           ":" + String.format( "%02d", sel_len / wavFile.getSampleRate() % 60 ) +
                                           "." + String.format( "%02d", ( sel_len % wavFile.getSampleRate() * 1000 ) / wavFile.getSampleRate() ) + " )" );
        sel_len_spinner.getValueFactory().setValue( sel_len );
        inv_select = false;
    }

}

/* TODO:
* - Make get_compact_samples() use more values when computing min/max values; optimize to minimize reads.
* - Use correct number of samples for windows smaller than the display width
* - Fix bugs....
* - Implement get_compact_samples() branch for when sample_number is larger than the cache size.
* - Add controls for: zoom factor, window size;
 */