package AudioDataSource.FileADS;

import AudioDataSource.IAudioDataSource;

/**
 * Created by Alex on 19.12.2017.
 */
public interface IFileAudioDataSource extends IAudioDataSource
{
    String getFile_path();
}
