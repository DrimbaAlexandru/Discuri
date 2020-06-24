package GUI.UI_Components;

import AudioDataSource.CachedADS.CachedAudioDataSource;
import AudioDataSource.FileADS.FileAudioSourceFactory;
import AudioDataSource.IAudioDataSource;
import Utils.Exceptions.DataSourceException;
import Utils.Exceptions.DataSourceExceptionCause;
import ProjectManager.*;
import Effects.Copy_to_ADS;
import Effects.IEffect;
import Utils.DataTypes.Interval;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.Pane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;

/**
 * Created by Alex on 19.06.2018.
 */

public class Export_Progress_Bar_Dialog
{
    @FXML
    private ProgressIndicator progress_bar;
    @FXML
    private Label lbl_progress;

    private Pane main_layout;
    private Stage onTop = new Stage();
    private DataSourceException close_exception = null;

    private IEffect effect;
    private Interval applying_interval;
    private IAudioDataSource source_ADS = null;
    private String destPath;
    private boolean finished_working = false;

    private void worker_thread()
    {
        try
        {
            ProjectManager.lock_access();
            if( source_ADS == null )
            {
                throw new DataSourceException( "Data source is not set", DataSourceExceptionCause.INVALID_STATE );
            }
            IAudioDataSource file = FileAudioSourceFactory.createFile( destPath, source_ADS.get_channel_number(), source_ADS.get_sample_rate(), 2 );
            effect.apply( source_ADS, file, applying_interval );
            file.close();
        }
        catch( DataSourceException e )
        {
            close_exception = e;
        }
        finally
        {
            ProjectManager.release_access();
        }
        finished_working = true;
    }

    private void UI_updater_thread()
    {
        while( !onTop.isShowing() )
        {
            try
            {
                Thread.sleep( 200 );
            }
            catch( InterruptedException e )
            {
                break;
            }
        }
        while( !finished_working )
        {
            Platform.runLater( () ->
                               {
                                   progress_bar.setProgress( effect.getProgress() );
                                   lbl_progress.setText( ( int )( effect.getProgress() * 100 ) + " %" );
                               } );
            try
            {
                Thread.sleep( 200 );
            }
            catch( InterruptedException e )
            {
                break;
            }
        }
        Platform.runLater( onTop::close );
    }

    public void show( Window parent )
    {
        onTop.initOwner( parent );
        onTop.initModality( Modality.WINDOW_MODAL );
        onTop.setResizable( false );
        onTop.setScene( new Scene( main_layout ) );
        onTop.setOnShown( ( ev ) ->
                          {
                              //HERE DO MAGIC STUFF
                              Thread worker = new Thread( this::worker_thread );
                              worker.start();
                              Thread ui_updater = new Thread( this::UI_updater_thread );
                              ui_updater.start();
                          } );
        //This blocks until the window closes
        onTop.showAndWait();
    }

    public Export_Progress_Bar_Dialog( String filePath, Interval applying_interval, IAudioDataSource source ) throws IOException, DataSourceException
    {
        FXMLLoader l = new FXMLLoader();
        l.setController( this );
        l.setLocation( ProjectStatics.getFxml_files_path( "progress_bar.fxml" ) );
        main_layout = l.load();
        if( main_layout == null )
        {
            throw new IOException( "Failed to load FXML file" );
        }
        destPath = filePath;
        this.source_ADS = source;
        this.applying_interval = new Interval( applying_interval.l, applying_interval.r, false );
        effect = new Copy_to_ADS();
    }

    public DataSourceException get_close_exception()
    {
        return close_exception;
    }
}
