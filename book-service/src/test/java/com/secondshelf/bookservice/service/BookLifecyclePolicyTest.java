package com.secondshelf.bookservice.service;

import com.secondshelf.bookservice.entity.Book;
import com.secondshelf.bookservice.entity.BookStatus;
import com.secondshelf.bookservice.entity.BookVisibility;
import com.secondshelf.bookservice.exception.BookNotFoundException;
import com.secondshelf.bookservice.exception.BookStateConflictException;
import com.secondshelf.bookservice.exception.ForbiddenOperationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BookLifecyclePolicyTest {

    private final BookLifecyclePolicy policy = new BookLifecyclePolicy();

    @Test
    void availableBookShouldAllowOwnerModificationReserveAndPublicVisibility() {
        Book book = book(1L, BookVisibility.PUBLIC, BookStatus.AVAILABLE);

        assertTrue(policy.canModifyByOwner(book));
        assertTrue(policy.canPublish(book));
        assertTrue(policy.canHide(book));
        assertTrue(policy.canDelete(book));
        assertTrue(policy.canReserve(book));
        assertFalse(policy.canMakeAvailable(book));
        assertFalse(policy.canMarkExchanged(book));
        assertTrue(policy.canBeVisibleInPublicCatalog(book));
    }

    @Test
    void reservedBookShouldBlockOwnerModificationAndPublicVisibility() {
        Book book = book(2L, BookVisibility.PUBLIC, BookStatus.RESERVED);

        assertFalse(policy.canModifyByOwner(book));
        assertFalse(policy.canPublish(book));
        assertFalse(policy.canHide(book));
        assertFalse(policy.canDelete(book));
        assertFalse(policy.canReserve(book));
        assertTrue(policy.canMakeAvailable(book));
        assertTrue(policy.canMarkExchanged(book));
        assertFalse(policy.canBeVisibleInPublicCatalog(book));
    }

    @Test
    void exchangedBookShouldBlockFurtherLifecycleTransitions() {
        Book book = book(3L, BookVisibility.PRIVATE, BookStatus.EXCHANGED);

        assertFalse(policy.canModifyByOwner(book));
        assertFalse(policy.canPublish(book));
        assertFalse(policy.canHide(book));
        assertFalse(policy.canDelete(book));
        assertFalse(policy.canReserve(book));
        assertFalse(policy.canMakeAvailable(book));
        assertFalse(policy.canMarkExchanged(book));
        assertFalse(policy.canBeVisibleInPublicCatalog(book));
    }

    @Test
    void ownerModificationAssertionsShouldThrowExplicitCodeWhenBookIsNotAvailable() {
        Book reservedBook = book(4L, BookVisibility.PUBLIC, BookStatus.RESERVED);

        BookStateConflictException exception = assertThrows(
                BookStateConflictException.class,
                () -> policy.assertCanModifyByOwner(reservedBook)
        );

        assertEquals("BOOK_NOT_AVAILABLE_FOR_MODIFICATION", exception.getCode());
        assertEquals(
                "Book must be AVAILABLE for owner-side update/delete/publish/hide operations.",
                exception.getMessage()
        );
    }

    @Test
    void reserveAssertionShouldThrowExplicitCodeWhenBookIsNotReservable() {
        Book reservedBook = book(5L, BookVisibility.PUBLIC, BookStatus.RESERVED);

        ForbiddenOperationException exception = assertThrows(
                ForbiddenOperationException.class,
                () -> policy.assertCanReserve(reservedBook)
        );

        assertEquals("BOOK_NOT_RESERVABLE", exception.getCode());
        assertEquals("Book must be AVAILABLE to reserve.", exception.getMessage());
    }

    @Test
    void makeAvailableAssertionShouldThrowExplicitCodeWhenBookIsNotReleasable() {
        Book availableBook = book(6L, BookVisibility.PUBLIC, BookStatus.AVAILABLE);

        ForbiddenOperationException exception = assertThrows(
                ForbiddenOperationException.class,
                () -> policy.assertCanMakeAvailable(availableBook)
        );

        assertEquals("BOOK_NOT_RELEASABLE", exception.getCode());
        assertEquals("Book must be RESERVED to set AVAILABLE.", exception.getMessage());
    }

    @Test
    void markExchangedAssertionsShouldThrowExplicitCodeWhenBookIsNotReservableForCompletion() {
        Book availableBook = book(7L, BookVisibility.PUBLIC, BookStatus.AVAILABLE);

        BookStateConflictException ownerException = assertThrows(
                BookStateConflictException.class,
                () -> policy.assertCanMarkExchangedForOwner(availableBook)
        );
        ForbiddenOperationException internalException = assertThrows(
                ForbiddenOperationException.class,
                () -> policy.assertCanMarkExchangedInternally(availableBook)
        );

        assertEquals("BOOK_NOT_EXCHANGE_COMPLETABLE", ownerException.getCode());
        assertEquals("BOOK_NOT_EXCHANGE_COMPLETABLE", internalException.getCode());
        assertEquals("Book must be RESERVED to mark as EXCHANGED.", ownerException.getMessage());
        assertEquals("Book must be RESERVED to mark as EXCHANGED.", internalException.getMessage());
    }

    @Test
    void publicVisibilityAssertionShouldThrowExplicitNotFoundCodeWhenBookIsHiddenFromCatalog() {
        Book reservedBook = book(8L, BookVisibility.PUBLIC, BookStatus.RESERVED);

        BookNotFoundException exception = assertThrows(
                BookNotFoundException.class,
                () -> policy.assertCanBeVisibleInPublicView(reservedBook)
        );

        assertEquals("BOOK_NOT_AVAILABLE_FOR_PUBLIC_VIEW", exception.getCode());
        assertEquals("Book with id = 8 not found.", exception.getMessage());
    }

    private Book book(Long id, BookVisibility visibility, BookStatus status) {
        return Book.builder()
                .id(id)
                .ownerId(42L)
                .title("Book " + id)
                .author("Author")
                .visibility(visibility)
                .status(status)
                .build();
    }
}
