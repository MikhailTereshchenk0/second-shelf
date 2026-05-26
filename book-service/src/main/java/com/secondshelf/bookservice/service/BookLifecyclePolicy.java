package com.secondshelf.bookservice.service;

import com.secondshelf.bookservice.entity.Book;
import com.secondshelf.bookservice.entity.BookStatus;
import com.secondshelf.bookservice.entity.BookVisibility;
import com.secondshelf.bookservice.exception.BookNotFoundException;
import com.secondshelf.bookservice.exception.BookStateConflictException;
import com.secondshelf.bookservice.exception.ForbiddenOperationException;
import org.springframework.stereotype.Component;

@Component
public class BookLifecyclePolicy {

    private static final String OWNER_MODIFICATION_MESSAGE =
            "Book must be AVAILABLE for owner-side update/delete/publish/hide operations.";
    private static final String RESERVE_MESSAGE = "Book must be AVAILABLE to reserve.";
    private static final String MAKE_AVAILABLE_MESSAGE = "Book must be RESERVED to set AVAILABLE.";
    private static final String MARK_EXCHANGED_MESSAGE = "Book must be RESERVED to mark as EXCHANGED.";

    public boolean canModifyByOwner(Book book) {
        return book.getStatus() == BookStatus.AVAILABLE;
    }

    public boolean canPublish(Book book) {
        return canModifyByOwner(book);
    }

    public boolean canHide(Book book) {
        return canModifyByOwner(book);
    }

    public boolean canDelete(Book book) {
        return canModifyByOwner(book);
    }

    public boolean canReserve(Book book) {
        return book.getStatus() == BookStatus.AVAILABLE;
    }

    public boolean canMakeAvailable(Book book) {
        return book.getStatus() == BookStatus.RESERVED;
    }

    public boolean canMarkExchanged(Book book) {
        return book.getStatus() == BookStatus.RESERVED;
    }

    public boolean canBeVisibleInPublicCatalog(Book book) {
        return book.getVisibility() == BookVisibility.PUBLIC
                && book.getStatus() == BookStatus.AVAILABLE;
    }

    public void assertCanModifyByOwner(Book book) {
        if (!canModifyByOwner(book)) {
            throw new BookStateConflictException(
                    "BOOK_NOT_AVAILABLE_FOR_MODIFICATION",
                    OWNER_MODIFICATION_MESSAGE
            );
        }
    }

    public void assertCanPublish(Book book) {
        if (!canPublish(book)) {
            throw new BookStateConflictException(
                    "BOOK_NOT_AVAILABLE_FOR_MODIFICATION",
                    OWNER_MODIFICATION_MESSAGE
            );
        }
    }

    public void assertCanHide(Book book) {
        if (!canHide(book)) {
            throw new BookStateConflictException(
                    "BOOK_NOT_AVAILABLE_FOR_MODIFICATION",
                    OWNER_MODIFICATION_MESSAGE
            );
        }
    }

    public void assertCanDelete(Book book) {
        if (!canDelete(book)) {
            throw new BookStateConflictException(
                    "BOOK_NOT_AVAILABLE_FOR_MODIFICATION",
                    OWNER_MODIFICATION_MESSAGE
            );
        }
    }

    public void assertCanReserve(Book book) {
        if (!canReserve(book)) {
            throw new ForbiddenOperationException("BOOK_NOT_RESERVABLE", RESERVE_MESSAGE);
        }
    }

    public void assertCanMakeAvailable(Book book) {
        if (!canMakeAvailable(book)) {
            throw new ForbiddenOperationException("BOOK_NOT_RELEASABLE", MAKE_AVAILABLE_MESSAGE);
        }
    }

    public void assertCanMarkExchangedForOwner(Book book) {
        if (!canMarkExchanged(book)) {
            throw new BookStateConflictException(
                    "BOOK_NOT_EXCHANGE_COMPLETABLE",
                    MARK_EXCHANGED_MESSAGE
            );
        }
    }

    public void assertCanMarkExchangedInternally(Book book) {
        if (!canMarkExchanged(book)) {
            throw new ForbiddenOperationException(
                    "BOOK_NOT_EXCHANGE_COMPLETABLE",
                    MARK_EXCHANGED_MESSAGE
            );
        }
    }

    public void assertCanBeVisibleInPublicView(Book book) {
        if (!canBeVisibleInPublicCatalog(book)) {
            throw BookNotFoundException.notAvailableForPublicView(book.getId());
        }
    }
}
