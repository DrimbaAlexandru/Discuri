package GUI.UI_Components;

import Exceptions.DataSourceException;
import ProjectManager.*;
import SignalProcessing.Effects.IEffect;
import Utils.Interval;
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
 * Created by Alex on 18.06.2018.
 */
public class Effect_Progress_Bar_Dialog
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
    private boolean finished_working = false;
    private final boolean create_new_project_version;

    private void worker_thread()
    {
        try
        {
            ProjectManager.lock_access();
            if( create_new_project_version )
            {
                ProjectManager.apply_effect( effect, applying_interval );
            }
            else
            {
                ProjectManager.apply_read_only_effect( effect, applying_interval );
            }
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
                Thread.sleep( 50 );
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
                Thread.sleep( 50 );
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

    public Effect_Progress_Bar_Dialog( IEffect effect, Interval interval, boolean create_new_project_version ) throws IOException, DataSourceException
    {
        FXMLLoader l = new FXMLLoader();
        l.setController( this );
        l.setLocation( ProjectStatics.getFxml_files_path( "progress_bar.fxml" ) );
        main_layout = l.load();
        if( main_layout == null )
        {
            throw new IOException( "Failed to load FXML file" );
        }
        this.effect = effect;
        this.applying_interval = new Interval( interval.l, interval.get_length() );
        this.create_new_project_version = create_new_project_version;
    }

    public DataSourceException get_close_exception()
    {
        return close_exception;
    }
}
