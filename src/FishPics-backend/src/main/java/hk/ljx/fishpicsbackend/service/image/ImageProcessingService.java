package hk.ljx.fishpicsbackend.service.image;

import hk.ljx.fishpicsbackend.dto.picture.*;
import hk.ljx.fishpicsbackend.entity.Picture;

import java.awt.image.BufferedImage;
import java.io.IOException;

public interface ImageProcessingService {

    BufferedImage readImage(String url) throws IOException;

    Picture validateAndGetPicture(Long pictureId);

    String compressPicture(ImageCompressRequest request);

    String generateThumbnail(ImageThumbnailRequest request);

    String rotatePicture(ImageRotateRequest request);

    String autoOrientPicture(Long pictureId);

    String addWatermarkAdvanced(ImageWatermarkAdvancedRequest request);

    String convertFormat(ImageFormatConvertRequest request);

    ImageBatchResult batchProcess(ImageBatchRequest request);

    String cleanMetadata(Long pictureId);

    BufferedImage handleHeic(byte[] heicBytes) throws IOException;

    byte[] imageToBytes(BufferedImage image, String format) throws IOException;

    String uploadAndUpdate(Picture picture, String oldUrl, BufferedImage image, String format);
}