package gov.cms.bfd.server.war.stu3.providers;

import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.param.ParamPrefixEnum;
import java.util.Date;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

/** As set of methods to help form JPA queries. */
public class QueryUtils {
  /**
   * Create a predicate for the lastUpdate field based on the passed range.
   *
   * @param criteriaBuilder to use
   * @param root to use
   * @param range to base the predicate on
   * @return a predicate on the lastUpdated field
   */
  @SuppressWarnings({"rawtypes", "unchecked"})
  static Predicate createLastUpdatedPredicate(
      CriteriaBuilder criteriaBuilder, Root root, DateRangeParam range) {
    Date lowerBound = range.getLowerBoundAsInstant();
    Date upperBound = range.getUpperBoundAsInstant();
    Path lastUpdatedPath = root.get("lastUpdated");

    Predicate lowerBoundPredicate = null;
    if (lowerBound != null) {
      switch (range.getLowerBound().getPrefix()) {
        case GREATERTHAN:
          lowerBoundPredicate = criteriaBuilder.greaterThan(lastUpdatedPath, lowerBound);
          break;
        case EQUAL:
        case GREATERTHAN_OR_EQUALS:
          lowerBoundPredicate = criteriaBuilder.greaterThanOrEqualTo(lastUpdatedPath, lowerBound);
          break;
        case STARTS_AFTER:
        case APPROXIMATE:
        case ENDS_BEFORE:
        case LESSTHAN:
        case LESSTHAN_OR_EQUALS:
        case NOT_EQUAL:
        default:
          throw new IllegalArgumentException("_lastUpdate lower bound has invalid prefix");
      }
    }

    Predicate upperBoundPredicate = null;
    if (upperBound != null) {
      switch (range.getUpperBound().getPrefix()) {
        case EQUAL:
          if (range.getLowerBound().getPrefix() == ParamPrefixEnum.EQUAL) {
            upperBoundPredicate = criteriaBuilder.lessThanOrEqualTo(lastUpdatedPath, upperBound);
          } else {
            throw new IllegalArgumentException(
                "_lastUpdate lower bound should have an equal prefix when the upper bound does");
          }
          break;
        case LESSTHAN:
          upperBoundPredicate = criteriaBuilder.lessThan(lastUpdatedPath, upperBound);
          break;
        case LESSTHAN_OR_EQUALS:
          upperBoundPredicate = criteriaBuilder.lessThanOrEqualTo(lastUpdatedPath, upperBound);
          break;
        case ENDS_BEFORE:
        case APPROXIMATE:
        case STARTS_AFTER:
        case GREATERTHAN:
        case GREATERTHAN_OR_EQUALS:
        case NOT_EQUAL:
        default:
          throw new IllegalArgumentException("_lastUpdate upper bound has invalid prefix");
      }
    }

    if (lowerBoundPredicate != null && upperBoundPredicate != null) {
      return criteriaBuilder.and(lowerBoundPredicate, upperBoundPredicate);
    } else if (lowerBoundPredicate != null) {
      return lowerBoundPredicate;
    } else {
      return upperBoundPredicate;
    }
  }

  /**
   * Create a predicate for the lastUpdate field based on the passed range.
   *
   * @param lastUpdated date to test
   * @param range to base test against
   * @return true iff within the range specified
   */
  static boolean isInRange(Date lastUpdated, DateRangeParam range) {
    if (range == null || range.isEmpty()) {
      return true;
    }
    Date lowerBound = range.getLowerBoundAsInstant();
    Date upperBound = range.getUpperBoundAsInstant();

    if (lowerBound != null) {
      switch (range.getLowerBound().getPrefix()) {
        case GREATERTHAN:
          if (lowerBound.compareTo(lastUpdated) <= 0) {
            return false;
          }
          break;
        case EQUAL:
        case GREATERTHAN_OR_EQUALS:
          if (lowerBound.compareTo(lastUpdated) < 0) {
            return false;
          }
        case STARTS_AFTER:
        case APPROXIMATE:
        case ENDS_BEFORE:
        case LESSTHAN:
        case LESSTHAN_OR_EQUALS:
        case NOT_EQUAL:
        default:
          throw new IllegalArgumentException("_lastUpdate lower bound has invalid prefix");
      }
    }

    if (upperBound != null) {
      switch (range.getUpperBound().getPrefix()) {
        case EQUAL:
          if (range.getLowerBound().getPrefix() == ParamPrefixEnum.EQUAL) {
            if (upperBound.compareTo(lastUpdated) > 0) {
              return false;
            }
          } else {
            throw new IllegalArgumentException(
                "_lastUpdate lower bound should have an equal prefix when the upper bound does");
          }
          break;
        case LESSTHAN:
          if (upperBound.compareTo(lastUpdated) >= 0) {
            return false;
          }
          break;
        case LESSTHAN_OR_EQUALS:
          if (upperBound.compareTo(lastUpdated) > 0) {
            return false;
          }
          break;
        case ENDS_BEFORE:
        case APPROXIMATE:
        case STARTS_AFTER:
        case GREATERTHAN:
        case GREATERTHAN_OR_EQUALS:
        case NOT_EQUAL:
        default:
          throw new IllegalArgumentException("_lastUpdate upper bound has invalid prefix");
      }
    }
    return true;
  }
}
