package hk.ljx.fishpicsbackend.picture.constants;

public final class PictureConstants {

    private PictureConstants() {
    }

    public static final int STATUS_DISABLED = 0;
    public static final int STATUS_APPROVED = 1;
    public static final int STATUS_PENDING_REVIEW = 2;

    public static final int ADMIN_FILTER_FEATURED = 4;
    public static final int ADMIN_FILTER_FEATURED_PENDING = 5;

    public static final int SELECTED_NORMAL = 0;
    public static final int SELECTED_FEATURED = 1;
    public static final int SELECTED_PENDING = 2;

    public static final int PRIVATE_PUBLIC = 0;
}
